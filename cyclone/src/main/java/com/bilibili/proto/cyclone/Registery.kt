package com.bilibili.proto.cyclone

import com.google.protobuf.ExtensionRegistry

/**
 * 描述：注册所有需要解析的extensions.
 *
 * Created by gongzhen on 2022/6/5
 */

fun registerAllExtensions(): ExtensionRegistry {

    // 注册自定义options.
    val registry = ExtensionRegistry.newInstance()


    /**
     * 注册google api client扩展.
     *
     * 解析
     *  method      methodSignature
     *  service     defaultHost
     * */
 //   ClientProto.registerAllExtensions(registry)

    /**
     * 注册google api annotations扩展
     * 解析
     *  method      http
     */
 //   AnnotationsProto.registerAllExtensions(registry)

    // 注册其他扩展.

    return registry
}