package org.jglrxavpok.vulkan

import org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR
import org.lwjgl.vulkan.VkSurfaceFormatKHR

data class SwapChainSupportDetails(var capabilities: VkSurfaceCapabilitiesKHR, val formats: VkSurfaceFormatKHR.Buffer, val presentModes: Array<VkPresentModeKHR>) {
}