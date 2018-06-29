package org.jglrxavpok.vulkan

data class QueueFamilyIndices(var graphicsFamily: Int = -1, var presentFamily: Int = -1) {

    val isComplete get()= graphicsFamily >= 0 && presentFamily >= 0
}