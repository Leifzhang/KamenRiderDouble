package com.bilibili.w.sample

/**
 *
 *  @Author LiABao
 *  @Since 2023/9/1
 *
 */
@JvmDefaultWithCompatibility
public interface DefaultInterfaceTest {

    public   fun test() {

    }

    fun ab()
}

@JvmDefaultWithoutCompatibility
public interface DefaultInterfaceTestWithout {

    public fun test1() {

    }

    fun ab2()
}

class Test : DefaultInterfaceTest, DefaultInterfaceTestWithout {
    override fun ab() {

    }

    override fun ab2() {

    }

}