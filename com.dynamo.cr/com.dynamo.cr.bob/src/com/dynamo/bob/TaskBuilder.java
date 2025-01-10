// Copyright 2020-2024 The Defold Foundation
// Copyright 2014-2020 King
// Copyright 2009-2014 Ragnar Svensson, Christian Murray
// Licensed under the Defold License version 1.0 (the "License"); you may not use
// this file except in compliance with the License.
//
// You may obtain a copy of the License, together with FAQs at
// https://www.defold.com/license
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

package com.dynamo.bob;

import com.dynamo.bob.TaskResult;
import com.dynamo.bob.TaskResult.Result;
import com.dynamo.bob.fs.IResource;
import com.dynamo.bob.bundle.BundleHelper;
import com.dynamo.bob.util.TimeProfiler;
import com.dynamo.bob.util.TimeProfiler.ProfilingScope;
import com.dynamo.bob.util.StringUtil;
import com.dynamo.bob.cache.ResourceCache;
import com.dynamo.bob.cache.ResourceCacheKey;
import com.dynamo.bob.logging.Logger;

import java.util.Arrays;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.EnumSet;
import java.util.HashSet;

import java.io.IOException;
import java.lang.Throwable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;


public class TaskBuilder {

    private static Logger logger = Logger.getLogger(TaskBuilder.class.getName());

    // set of all completed tasks. The set includes both task run
    // in this session and task already completed (output already exists with correct signatures, see below)
    // the set also contains failed tasks
    private Set<Task> completedTasks = new HashSet<>();

    // the set of all output files generated
    // in this or previous session
    private Set<IResource> completedOutputs = new HashSet<>();

    // set of *all* possible output files
    private Set<IResource> allOutputs = new HashSet<>();

    // task results
    private List<TaskResult> results = new ArrayList<>();

    // the tasks to build
    // private List<Task> tasks;
    private Set<Task> tasks;

    private Project project;
    private Map<String, String> options;
    private State state;
    private ResourceCache resourceCache;

    // This flag is needed to determine if at least one file was built or taken from the cache.
    // It is used to know whether GameProjectBuilder.build() (the builder for `game.project`)
    // needs to be executed.
    private boolean buildContainsChanges;

    // for executing the individual tasks
    private ExecutorService  executorService;
    private int nThreads;

    public TaskBuilder(List<Task> tasks, Project project) {
        this.tasks = new HashSet<Task>(tasks);
        this.project = project;
        this.options = project.getOptions();
        this.state = project.getState();
        this.resourceCache = project.getResourceCache();

        for (Task task : this.tasks) {
            allOutputs.addAll(task.getOutputs());
        }

        this.nThreads = project.getMaxCpuThreads();
        logger.info("Creating task builder with a fixed thread pool executor using %d threads", this.nThreads);
        this.executorService = Executors.newFixedThreadPool(this.nThreads);
    }

    private Callable<TaskResult> createCallableTask(final Task task, final IProgress monitor) {
        Callable<TaskResult> callableTask = () -> {
            TaskResult result = buildTask(task, monitor);
            return result;
        };
        return callableTask;
    }

    private boolean compareAllSignatures(byte[] taskSignature, List<IResource> outputResources) {
        boolean allSigsEquals = true;
        for (IResource r : outputResources) {
            byte[] s = state.getSignature(r.getAbsPath());
            if (!Arrays.equals(s, taskSignature)) {
                allSigsEquals = false;
                break;
            }
        }
        return allSigsEquals;
    }

    // deps are the task input files generated by another task not yet completed,
    // i.e. "solve" the dependency graph
    private boolean hasUnresolvedDependencies(Task task) {
        Set<IResource> dependencies = getUnresolvedDependencies(task);
        return !dependencies.isEmpty();
    }
    private Set<IResource> getUnresolvedDependencies(Task task) {
        Set<IResource> dependencies = new HashSet<>(task.getInputs());
        dependencies.retainAll(allOutputs);
        dependencies.removeAll(completedOutputs);
        return dependencies;
    }


    private boolean checkIfResourcesExist(final List<IResource> resources) {
        // do all output files exist?
        boolean allResourcesExists = true;
        for (IResource r : resources) {
            if (!r.exists()) {
                allResourcesExists = false;
                break;
            }
        }
        return allResourcesExists;
    }

    private TaskResult buildTask(Task task, IProgress monitor) throws IOException {
        BundleHelper.throwIfCanceled(monitor);

        TimeProfiler.start(task.getName());

        TaskResult taskResult = new TaskResult(task);
        taskResult.setResult(Result.SUCCESS);
        taskResult.setProfilingScope(TimeProfiler.getCurrentScope());

        try {
            final List<IResource> outputResources = task.getOutputs();
            // check if all outputs already exist
            final boolean allOutputsExist = checkIfResourcesExist(outputResources);

            // compare all task signature. current task signature between previous
            // signature from state on disk
            final byte[] taskSignature = task.calculateSignature();
            final boolean allSigsEquals = compareAllSignatures(taskSignature, outputResources);

            Builder builder = task.getBuilder();
            Map<IResource, String> outputResourceToCacheKey = new HashMap<IResource, String>();
            
            // build the task if outputs are missing
            // or signatures are not matching
            // or if the build contains changes and this task is building game.project
            if (!allSigsEquals || !allOutputsExist || (buildContainsChanges && builder.isGameProjectBuilder())) {
                if (task.isCacheable() && resourceCache.isCacheEnabled()) {
                    // check if all output resources exist in the resource cache
                    boolean allResourcesCached = true;
                    for (IResource r : outputResources) {
                        final String key = ResourceCacheKey.calculate(task, options, r);
                        outputResourceToCacheKey.put(r, key);
                        if (!r.isCacheable()) {
                            allResourcesCached = false;
                        }
                        else if (!resourceCache.contains(key)) {
                            allResourcesCached = false;
                        }
                    }

                    // all resources exist in the cache
                    // copy them to the output
                    if (allResourcesCached) {
                        TimeProfiler.addData("takenFromCache", true);
                        for (IResource r : outputResources) {
                            r.setContent(resourceCache.get(outputResourceToCacheKey.get(r)));
                        }
                    }
                    // build task and cache output
                    else {
                        builder.build(task);
                        for (IResource r : outputResources) {
                            state.putSignature(r.getAbsPath(), taskSignature);
                            if (r.isCacheable()) {
                                resourceCache.put(outputResourceToCacheKey.get(r), r.getContent());
                            }
                        }
                    }
                }
                else {
                    builder.build(task);
                    for (IResource r : outputResources) {
                        state.putSignature(r.getAbsPath(), taskSignature);
                    }
                }
                monitor.worked(1);
                buildContainsChanges = true;

                // verify that all output resources were created
                for (IResource r : outputResources) {
                    if (!r.exists()) {
                        taskResult.setResult(Result.FAILED);
                        taskResult.setLineNumber(0);
                        taskResult.setMessage(String.format("Output '%s' not found", r.getAbsPath()));
                        break;
                    }
                }
            }
            else {
                taskResult.setResult(Result.SKIPPED);
            }

        } catch (CompileExceptionError e) {
            taskResult.setResult(Result.FAILED);
            taskResult.setLineNumber(e.getLineNumber());
            taskResult.setMessage(e.getMessage());
            e.printStackTrace(new java.io.PrintStream(System.out));
        } catch (OutOfMemoryError e) {
            taskResult.setResult(Result.RETRY);
            taskResult.setMessage(e.getMessage());
            taskResult.setException(e);
            e.printStackTrace(new java.io.PrintStream(System.out));
        } catch (Throwable e) {
            taskResult.setResult(Result.FAILED);
            taskResult.setLineNumber(0);
            taskResult.setMessage(e.getMessage());
            taskResult.setException(e);
            e.printStackTrace(new java.io.PrintStream(System.out));
        }
        TimeProfiler.addData("output", StringUtil.truncate(task.getOutputsString(), 1000));
        TimeProfiler.addData("type", "buildTask");
        TimeProfiler.stop();
        return taskResult;
    }


    public Set<IResource> getAllOutputs() {
        return allOutputs;
    }


    @SuppressWarnings({ "rawtypes", "unchecked" })
    public List<TaskResult> build(IProgress monitor) throws IOException, CompileExceptionError {
        TimeProfiler.start("Build tasks");
        logger.info("Build tasks");
        long tstart = System.currentTimeMillis();

        buildContainsChanges = false;
        boolean abort = false;
        int maxConcurrentHighMemoryTasks = this.nThreads;
        List<Callable<TaskResult>> tasksToSubmit = new ArrayList<>();
        Map<String, Integer> taskNameCounter = new HashMap<>();
        while (!tasks.isEmpty() && !abort) {

            // create a list of tasks to build this iteration
            // - ignore tasks with unresolved dependencies
            // - build game.project last as the only remaining task (see #9553)
            // - never build more than one material or vp/fp at a time since
            //   there is some kind of threading issue with the command line
            //   tools
            // - restrict atlas/tileset builders per iteration (for memory reasons)
            int remainingTasksCount = tasks.size();
            tasksToSubmit.clear();
            taskNameCounter.clear();
            for (Task task : tasks) {
                if (task.getBuilder().isGameProjectBuilder() && remainingTasksCount > 1) continue;
                String taskName = task.getName();
                if (taskName.equals("FragmentProgram") || taskName.equals("VertexProgram") || taskName.equals("Material")) taskName = "Shader";
                if (taskName.equals("TileSet")) taskName = "Atlas";
                int count = taskNameCounter.getOrDefault(taskName, 0);
                if (taskName.equals("Atlas") && (count == maxConcurrentHighMemoryTasks)) continue;
                if (taskName.equals("Shader") && count == 1) continue;
                if (hasUnresolvedDependencies(task)) continue;
                tasksToSubmit.add(createCallableTask(task, monitor));
                taskNameCounter.put(taskName, count + 1);
            }

            try {
                boolean retryAnyTask = false;
                List<Future<TaskResult>> futures = this.executorService.invokeAll(tasksToSubmit);
                for (Future<TaskResult> future : futures) {
                    TaskResult result = future.get();
                    Task task = result.getTask();

                    // should the task be retried again?
                    // this can happen if running out of memory while building
                    if (result.getResult() == Result.RETRY) {
                        retryAnyTask = true;
                        if (maxConcurrentHighMemoryTasks > 1) {
                            maxConcurrentHighMemoryTasks--;
                            logger.warning("Task '%s' (%s) must be retried. Reducing number of concurrent high memory tasks to %d", task.getName(), task.getInputsString(), maxConcurrentHighMemoryTasks);
                        }
                        continue;
                    }

                    results.add(result);
                    if (result.isOk()) {
                        ProfilingScope taskScope = result.getProfilingScope();
                        TimeProfiler.addScopeToCurrentThread(taskScope);
                        completedTasks.add(task);
                        completedOutputs.addAll(task.getOutputs());
                        boolean success = tasks.remove(task);
                        if (!success) {
                            // this shouldn't really happen, but we might as well
                            // check for it anyway
                            logger.severe("Unable to find task to remove");
                            abort = true;
                            break;
                        }
                    }
                    else {
                        List<IResource> outputs = task.getOutputs();
                        for (IResource o : outputs) {
                            state.removeSignature(o.getAbsPath());
                        }
                        logger.severe("Task '%s' (%s) failed", task.getName(), task.getInputsString());
                        abort = true;
                        break;
                    }
                }
                // reset cap on max number of concurrent high memory tasks if
                // no tasks required a retry
                if (!retryAnyTask) {
                    maxConcurrentHighMemoryTasks = this.nThreads;
                }
            }
            catch (Exception e) {
                if (!monitor.isCanceled()) {
                    logger.severe("Exception");
                    e.printStackTrace(new java.io.PrintStream(System.out));
                }
                abort = true;
            }
        }

        this.executorService.shutdownNow();

        long tend = System.currentTimeMillis();
        logger.info("Build tasks took %f s", (tend-tstart)/1000.0);
        TimeProfiler.stop();
        return results;
    }
}
