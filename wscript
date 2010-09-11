#! /usr/bin/env python

VERSION='0.1'
APPNAME='gui'

srcdir = '.'
blddir = 'build'

import sys
import waf_dynamo, waf_ddf

def init():
    pass

def set_options(opt):
    opt.tool_options('compiler_cc')
    opt.tool_options('compiler_cxx')
    opt.tool_options('waf_dynamo')

def configure(conf):
    conf.check_tool('compiler_cc')
    conf.check_tool('compiler_cxx')
    conf.check_tool('waf_dynamo')

    waf_ddf.configure(conf)

    conf.sub_config('src')

    conf.env.append_value('CPPPATH', "default/src")
    conf.env['LIB_GTEST'] = 'gtest'
    conf.env['STATICLIB_DLIB'] = 'dlib'
    conf.env['STATICLIB_LUA'] = 'lua'

    platform = conf.env['PLATFORM']

    if platform == "linux":
        conf.env['LIB_PLATFORM_SOCKET'] = ''
    elif 'darwin' in platform:
        conf.env['LIB_PLATFORM_SOCKET'] = ''
    else:
        conf.env['LIB_PLATFORM_SOCKET'] = 'WS2_32'

    conf.env.append_unique('CCDEFINES', 'DLIB_LOG_DOMAIN="GUI"')
    conf.env.append_unique('CXXDEFINES', 'DLIB_LOG_DOMAIN="GUI"')

def build(bld):
    bld.add_subdirs('src')

def shutdown():
    waf_dynamo.run_gtests(valgrind = True)
