package com.abominableenterprise.cherkit.analytics

import com.posthog.PostHogInterface
import com.posthog.android.PostHogAndroidConfig
import java.lang.reflect.Proxy

/** One call on a fake client: the method name and its arguments as the SDK interface received them. */
data class ClientCall(val method: String, val args: List<Any?>)

/**
 * A fake [PostHogInterface] that records every method call (so a test can prove nothing else, such as `identify`,
 * was called) and never touches the network. Built with a dynamic proxy, so it stays in sync with the SDK interface.
 */
class FakePostHogClient(val id: Int) {
    val calls = mutableListOf<ClientCall>()
    val failNext = mutableSetOf<String>()

    val methods: List<String> get() = calls.map { it.method }

    val client: PostHogInterface = Proxy.newProxyInstance(
        PostHogInterface::class.java.classLoader,
        arrayOf(PostHogInterface::class.java),
    ) { proxy, method, args ->
        when (method.name) {
            "equals" -> proxy === args?.get(0)
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "FakePostHogClient#$id"
            else -> {
                calls += ClientCall(method.name, args?.toList().orEmpty())
                if (failNext.remove(method.name)) throw IllegalStateException("injected ${method.name} failure")
                defaultReturn(method.returnType)
            }
        }
    } as PostHogInterface

    private fun defaultReturn(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        else -> null
    }
}

/** A factory that hands out a new [FakePostHogClient] per call and keeps every config it was given. */
internal class FakePostHogClientFactory {
    val clients = mutableListOf<FakePostHogClient>()
    val configs = mutableListOf<PostHogAndroidConfig>()

    val factory = PostHogClientFactory { config ->
        configs += config
        FakePostHogClient(clients.size + 1).also { clients += it }.client
    }
}
