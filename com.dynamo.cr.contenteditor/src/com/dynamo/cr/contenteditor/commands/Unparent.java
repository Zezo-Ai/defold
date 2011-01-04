package com.dynamo.cr.contenteditor.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;

import com.dynamo.cr.contenteditor.editors.IEditor;
import com.dynamo.cr.contenteditor.operations.UnparentOperation;
import com.dynamo.cr.contenteditor.scene.Node;

public class Unparent extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IEditorPart editor = HandlerUtil.getActiveEditor(event);
        if (editor instanceof IEditor) {
            Node[] selected_nodes = ((IEditor) editor).getSelectedNodes();
            if (selected_nodes.length > 0) {

                UnparentOperation op = new UnparentOperation(selected_nodes);
                ((IEditor) editor).executeOperation(op);
            }
        }
        return null;
    }

}
