package org.jglrxavpok.vulkan

import org.lwjgl.BufferUtils
import org.lwjgl.PointerBuffer
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWVulkan.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.EXTDebugReport.*
import org.lwjgl.vulkan.KHRSurface.*
import org.lwjgl.vulkan.KHRSwapchain.*
import org.lwjgl.vulkan.VK10.*
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import org.lwjgl.vulkan.KHRDisplaySwapchain.VK_ERROR_INCOMPATIBLE_DISPLAY_KHR
import javax.swing.JButton
import javax.swing.JFrame


object VulkanStart {

    const val nullptr = NULL
    const val UINT64_MAX: Long = -1

    private var running = true
    private var windowPointer: Long = -1
    const val WIDTH = 800
    const val HEIGHT = 600
    const val enableValidationLayers = true
    val validationLayers = arrayOf("VK_LAYER_LUNARG_standard_validation")
    val deviceExtensions = arrayOf(VK_KHR_SWAPCHAIN_EXTENSION_NAME)

    // Vulkan stuff
    private lateinit var vkInstance: VkInstance
    private lateinit var physicalDevice: VkPhysicalDevice
    private lateinit var logicalDevice: VkDevice
    private lateinit var graphicsQueue: VkQueue
    private lateinit var presentQueue: VkQueue
    private lateinit var indices: QueueFamilyIndices
    private var swapchain: VkSwapchainKHR = nullptr
    private var surface: VkSurfaceKHR = nullptr
    private lateinit var swapchainImages: Array<VkImage>
    private lateinit var swapchainDetails: SwapChainSupportDetails
    private lateinit var swapchainFormat: VkSurfaceFormatKHR
    private var swapchainImageFormat: VkFormat = 0
    private var swapchainPresentMode: VkPresentModeKHR = 0
    private var pipelineLayout: VkPipelineLayout = nullptr
    private var renderPass: VkRenderPass = nullptr
    private var graphicsPipeline: VkPipeline = nullptr
    private var commandPool: VkCommandPool = nullptr
    private var imageAvailableSemaphore: VkSemaphore = nullptr
    private var renderFinishedSemaphore: VkSemaphore = nullptr
    private lateinit var swapchainExtent: VkExtent2D
    private lateinit var swapchainImageViews: Array<VkImageView>
    private lateinit var swapchainFramebuffers: Array<VkFramebuffer>
    private lateinit var commandBuffers: Array<VkCommandBuffer>


    @JvmStatic
    fun main(args: Array<String>) {
        if("--waitRenderdoc" in args) {
            val frame = JFrame("Waiting for Renderdoc...")
            frame.add(JButton("Confirm that Renderdoc has been attached").apply {
                addActionListener {
                    frame.dispose()
                    start()
                }
            })
            frame.pack()
            frame.setLocationRelativeTo(null)
            frame.isVisible = true
        } else {
            start()
        }
    }

    private fun start() {
        init()
        initVulkan()
        run()
        cleanup()
    }

    private fun initVulkan() {
        vkInstance = createVulkanInstance()
        setupDebugCallback()
        surface = createSurface()
        physicalDevice = pickPhysicalDevice()
        indices = findQueueFamilies(physicalDevice)
        logicalDevice = createLogicalDevice()
        graphicsQueue = createGraphicsQueue()
        presentQueue = createPresentQueue()

        swapchainDetails = querySwapChainSupport(physicalDevice)
        swapchainFormat = chooseSwapSurfaceFormat(swapchainDetails.formats)
        swapchainImageFormat = swapchainFormat.format()
        swapchainPresentMode = chooseSwapPresentMode(swapchainDetails.presentModes)
        swapchainExtent = chooseSwapExtent(swapchainDetails.capabilities)
        val (swapchain, swapchainImages) = createSwapChain(swapchainDetails, swapchainFormat, swapchainExtent, swapchainPresentMode)
        this.swapchain = swapchain
        this.swapchainImages = swapchainImages

        swapchainImageViews = createImageViews()

        createRenderPass()
        createGraphicsPipeline()
        createFramebuffers()
        createCommandPool()
        createCommandBuffers()
        createSemaphores()
    }

    private fun createSemaphores() {
        val semaphoreInfo = VkSemaphoreCreateInfo.calloc()
        semaphoreInfo.sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO)

        MemoryStack.stackPush().use {
            val pAvailable = it.mallocLong(1)
            val pRender = it.mallocLong(1)

            if(vkCreateSemaphore(logicalDevice, semaphoreInfo, null, pAvailable) != VK_SUCCESS || vkCreateSemaphore(logicalDevice, semaphoreInfo, null, pRender) != VK_SUCCESS)
                error("Failed to create semaphores")

            imageAvailableSemaphore = pAvailable[0]
            renderFinishedSemaphore = pRender[0]
        }
    }

    private fun createCommandBuffers() {
        val allocInfo = VkCommandBufferAllocateInfo.calloc()
        allocInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
        allocInfo.commandPool(commandPool)
        allocInfo.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
        allocInfo.commandBufferCount(swapchainFramebuffers.size)

        MemoryStack.stackPush().use {
            val commandBufferPointers = it.mallocPointer(swapchainFramebuffers.size)
            if(vkAllocateCommandBuffers(logicalDevice, allocInfo, commandBufferPointers) != VK_SUCCESS) {
                error("Failed to allocate command buffers")
            }

            commandBuffers = Array(swapchainFramebuffers.size) { i ->
                VkCommandBuffer(commandBufferPointers[i], logicalDevice)
            }
        }

        for((index, commandBuffer) in commandBuffers.withIndex()) {
            val beginInfo = VkCommandBufferBeginInfo.calloc()
            beginInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
            beginInfo.flags(VK_COMMAND_BUFFER_USAGE_SIMULTANEOUS_USE_BIT)
            beginInfo.pInheritanceInfo(null)

            if(vkBeginCommandBuffer(commandBuffer, beginInfo) != VK_SUCCESS)
                error("Failed to begin recording of command buffer")

            val renderPassInfo = VkRenderPassBeginInfo.calloc()
            renderPassInfo.sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
            renderPassInfo.renderPass(renderPass)
            renderPassInfo.framebuffer(swapchainFramebuffers[index])
            val renderArea = VkRect2D.calloc()
            renderArea.offset(VkOffset2D.calloc().set(0,0))
            renderArea.extent(swapchainExtent)
            renderPassInfo.renderArea(renderArea)

            val clearValues = VkClearValue.calloc(1)
            clearValues.color().float32(0, 0f).float32(1, 1f).float32(2, 1f).float32(3, 1f)
            renderPassInfo.pClearValues(clearValues)

            vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE)

            // drawing commands
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline)
            vkCmdDraw(commandBuffer, 3, 1, 0, 0) // draw 3 vertices (1 instance)

            vkCmdEndRenderPass(commandBuffer)

            if(vkEndCommandBuffer(commandBuffer) != VK_SUCCESS)
                error("Failed to record command buffer")
        }
    }

    private fun createCommandPool() {
        val queueFamilyIndices = findQueueFamilies(physicalDevice)
        val poolInfo = VkCommandPoolCreateInfo.calloc()
        poolInfo.sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
        poolInfo.queueFamilyIndex(queueFamilyIndices.graphicsFamily)

        commandPool = MemoryStack.stackPush().use {
            val pPool = it.mallocLong(1)

            if(vkCreateCommandPool(logicalDevice, poolInfo, null, pPool) != VK_SUCCESS) {
                error("Failed to create command pool")
            }
            pPool[0]
        }
    }

    private fun init() {
        if(!glfwInit()) {
            error("Could not init GLFW")
        }
        if (!glfwVulkanSupported()) {
            error("Vulkan is not supported by GLFW")
        }

        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE)
        windowPointer = glfwCreateWindow(WIDTH, HEIGHT, "Vulkan test", nullptr, nullptr)
    }

    private fun run() {
        while(!glfwWindowShouldClose(windowPointer)) {
            glfwPollEvents()
            drawFrame()
        }
    }

    private fun drawFrame() {
        MemoryStack.stackPush().use { mem ->
            val imageIndex = mem.mallocInt(1)
            val err = vkAcquireNextImageKHR(logicalDevice, swapchain, UINT64_MAX, imageAvailableSemaphore, VK_NULL_HANDLE, imageIndex)
            if(err != VK_SUCCESS) {
                println("!! ${Integer.toHexString(err)} / $err -> ${translateVulkanResult(err)}")
            }

            imageIndex.rewind()

            val submitInfo = VkSubmitInfo.calloc()
            submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)

            val waitSemaphores = BufferUtils.createLongBuffer(1).put(imageAvailableSemaphore).flip() as LongBuffer
            val waitStages = BufferUtils.createIntBuffer(1).put(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT).flip() as IntBuffer
            submitInfo.pWaitDstStageMask(waitStages)
            submitInfo.pWaitSemaphores(waitSemaphores)
            submitInfo.waitSemaphoreCount(1)

            val commandBuffer = commandBuffers[imageIndex[0]]
            val pCommandBuffers = BufferUtils.createPointerBuffer(1)
                    .put(commandBuffer)
                    .flip()
            submitInfo.pCommandBuffers(pCommandBuffers)

            val signalSemaphores = BufferUtils.createLongBuffer(1).put(renderFinishedSemaphore).flip() as LongBuffer
            submitInfo.pSignalSemaphores(signalSemaphores)

            val err2 = vkQueueSubmit(graphicsQueue, submitInfo, VK_NULL_HANDLE)
            if(err2 != VK_SUCCESS) {
                error("Failed to submit draw command buffer ${translateVulkanResult(err2)}")
            }

            val presentInfo = VkPresentInfoKHR.calloc()
            presentInfo.sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
            presentInfo.pWaitSemaphores(signalSemaphores)
            presentInfo.pResults(null)
            presentInfo.swapchainCount(1)

            val swapchains = BufferUtils.createLongBuffer(1).put(swapchain).flip() as LongBuffer
            presentInfo.pSwapchains(swapchains)
            presentInfo.pImageIndices(imageIndex)

            vkQueuePresentKHR(presentQueue, presentInfo)

            presentInfo.pResults(null)

            vkQueueWaitIdle(presentQueue)
            vkDeviceWaitIdle(logicalDevice)
        }
    }

    fun translateVulkanResult(result: Int): String {
        when (result) {
        // Success codes
            VK_SUCCESS -> return "Command successfully completed."
            VK_NOT_READY -> return "A fence or query has not yet completed."
            VK_TIMEOUT -> return "A wait operation has not completed in the specified time."
            VK_EVENT_SET -> return "An event is signaled."
            VK_EVENT_RESET -> return "An event is unsignaled."
            VK_INCOMPLETE -> return "A return array was too small for the result."
            VK_SUBOPTIMAL_KHR -> return "A swapchain no longer matches the surface properties exactly, but can still be used to present to the surface successfully."

        // Error codes
            VK_ERROR_OUT_OF_HOST_MEMORY -> return "A host memory allocation has failed."
            VK_ERROR_OUT_OF_DEVICE_MEMORY -> return "A device memory allocation has failed."
            VK_ERROR_INITIALIZATION_FAILED -> return "Initialization of an object could not be completed for implementation-specific reasons."
            VK_ERROR_DEVICE_LOST -> return "The logical or physical device has been lost."
            VK_ERROR_MEMORY_MAP_FAILED -> return "Mapping of a memory object has failed."
            VK_ERROR_LAYER_NOT_PRESENT -> return "A requested layer is not present or could not be loaded."
            VK_ERROR_EXTENSION_NOT_PRESENT -> return "A requested extension is not supported."
            VK_ERROR_FEATURE_NOT_PRESENT -> return "A requested feature is not supported."
            VK_ERROR_INCOMPATIBLE_DRIVER -> return "The requested version of Vulkan is not supported by the driver or is otherwise incompatible for implementation-specific reasons."
            VK_ERROR_TOO_MANY_OBJECTS -> return "Too many objects of the type have already been created."
            VK_ERROR_FORMAT_NOT_SUPPORTED -> return "A requested format is not supported on this device."
            VK_ERROR_SURFACE_LOST_KHR -> return "A surface is no longer available."
            VK_ERROR_NATIVE_WINDOW_IN_USE_KHR -> return "The requested window is already connected to a VkSurfaceKHR, or to some other non-Vulkan API."
            VK_ERROR_OUT_OF_DATE_KHR -> return ("A surface has changed in such a way that it is no longer compatible with the swapchain, and further presentation requests using the "
                    + "swapchain will fail. Applications must query the new surface properties and recreate their swapchain if they wish to continue" + "presenting to the surface.")
            VK_ERROR_INCOMPATIBLE_DISPLAY_KHR -> return "The display used by a swapchain does not use the same presentable image layout, or is incompatible in a way that prevents sharing an" + " image."
            VK_ERROR_VALIDATION_FAILED_EXT -> return "A validation layer found an error."
            else -> return String.format("%s [%d]", "Unknown", Integer.valueOf(result))
        }
    }

    private fun cleanup() {
        vkDestroySemaphore(logicalDevice, renderFinishedSemaphore, null)
        vkDestroySemaphore(logicalDevice, imageAvailableSemaphore, null)
        vkDestroyCommandPool(logicalDevice, commandPool, null)
        for(framebuffer in swapchainFramebuffers) {
            vkDestroyFramebuffer(logicalDevice, framebuffer, null)
        }
        vkDestroyPipeline(logicalDevice, graphicsPipeline, null)
        vkDestroyPipelineLayout(logicalDevice, pipelineLayout, null)
        vkDestroyRenderPass(logicalDevice, renderPass, null)
        for(imageView in swapchainImageViews) {
            vkDestroyImageView(logicalDevice, imageView, null)
        }
        vkDestroySwapchainKHR(logicalDevice, swapchain, null)
        vkDestroyDevice(logicalDevice, null)
        vkDestroySurfaceKHR(vkInstance, surface, null)
        vkDestroyInstance(vkInstance, null)

        glfwDestroyWindow(windowPointer)
        glfwTerminate()
    }

    private fun createFramebuffers() {
        swapchainFramebuffers = Array<VkFramebuffer>(swapchainImageViews.size) { nullptr }
        for((index, view) in swapchainImageViews.withIndex()) {
            val attachments = memAllocLong(1)
            attachments.put(view).flip()

            val framebufferInfo = VkFramebufferCreateInfo.calloc()
            framebufferInfo.sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
            framebufferInfo.renderPass(renderPass)
            framebufferInfo.pAttachments(attachments)
            framebufferInfo.width(swapchainExtent.width())
            framebufferInfo.height(swapchainExtent.height())
            framebufferInfo.layers(1)

            swapchainFramebuffers[index] = MemoryStack.stackPush().use {
                val pFramebuffer = it.mallocLong(1)
                if(vkCreateFramebuffer(logicalDevice, framebufferInfo, null, pFramebuffer) != VK_SUCCESS) {
                    error("Failed to create framebuffer")
                }
                pFramebuffer[0]
            }

        }
    }

    private fun createRenderPass() {
        val colorAttachment = VkAttachmentDescription.calloc(1)
        colorAttachment.format(swapchainImageFormat)
        colorAttachment.samples(VK_SAMPLE_COUNT_1_BIT)

        colorAttachment.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
        colorAttachment.storeOp(VK_ATTACHMENT_STORE_OP_STORE)

        colorAttachment.stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
        colorAttachment.stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)

        colorAttachment.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
        colorAttachment.finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)

        val colorAttachmentRef = VkAttachmentReference.calloc(1)
        colorAttachmentRef.attachment(0)
        colorAttachmentRef.layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)

        val subpass = VkSubpassDescription.calloc(1)
        subpass.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
        subpass.colorAttachmentCount(1)
        subpass.pColorAttachments(colorAttachmentRef)

        val dependency = VkSubpassDependency.calloc(1)
        dependency.srcSubpass(VK_SUBPASS_EXTERNAL)
        dependency.dstSubpass(0)

        dependency.srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
        dependency.srcAccessMask(0)

        dependency.dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
        dependency.dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT or VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)

        val renderPassInfo = VkRenderPassCreateInfo.calloc()
        renderPassInfo.sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
        renderPassInfo.pAttachments(colorAttachment)
        renderPassInfo.pSubpasses(subpass)

        renderPassInfo.pDependencies(dependency)

        renderPass = MemoryStack.stackPush().use {
            val pRenderPass = it.mallocLong(1)
            if(vkCreateRenderPass(logicalDevice, renderPassInfo, null, pRenderPass) != VK_SUCCESS)
                error("Could not create render pass")
            pRenderPass[0]
        }
    }

    private fun createGraphicsPipeline() {
        val vertShaderCode = javaClass.getResourceAsStream("/shaders/vert.spv").readBytes()
        val fragShaderCode = javaClass.getResourceAsStream("/shaders/frag.spv").readBytes()
        val vertModule = createShaderModule(vertShaderCode)
        val fragModule = createShaderModule(fragShaderCode)

        val vertShaderStageInfo = VkPipelineShaderStageCreateInfo.calloc()
        vertShaderStageInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
        vertShaderStageInfo.stage(VK_SHADER_STAGE_VERTEX_BIT)
        vertShaderStageInfo.module(vertModule)
        vertShaderStageInfo.pName(memUTF8("main"))

        val fragShaderStageInfo = VkPipelineShaderStageCreateInfo.calloc()
        fragShaderStageInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
        fragShaderStageInfo.stage(VK_SHADER_STAGE_FRAGMENT_BIT)
        fragShaderStageInfo.module(fragModule)
        fragShaderStageInfo.pName(memUTF8("main"))

        val shaderStages = VkPipelineShaderStageCreateInfo.calloc(2).put(vertShaderStageInfo).put(fragShaderStageInfo).flip()

        val vertexInputInfo = VkPipelineVertexInputStateCreateInfo.calloc()
        vertexInputInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)

        val inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc()
        inputAssembly.sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
        inputAssembly.topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
        inputAssembly.primitiveRestartEnable(false)

        val viewport = VkViewport.calloc()
        viewport.x(0f)
        viewport.y(0f)
        viewport.width(swapchainExtent.width().toFloat())
        viewport.height(swapchainExtent.height().toFloat())
        viewport.minDepth(0f)
        viewport.maxDepth(1f)

        val scissor = VkRect2D.calloc()
        val offset = VkOffset2D.calloc()
        offset.set(0, 0)
        scissor.offset(offset)
        scissor.extent(swapchainExtent)

        val viewportState = VkPipelineViewportStateCreateInfo.calloc()
        viewportState.sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
        viewportState.viewportCount(1)
        viewportState.pViewports(VkViewport.calloc(1).put(viewport).flip())
        viewportState.scissorCount(1)
        viewportState.pScissors(VkRect2D.calloc(1).put(scissor).flip())

        val rasterizer = VkPipelineRasterizationStateCreateInfo.calloc()
        rasterizer.sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
        rasterizer.depthClampEnable(false)
        rasterizer.rasterizerDiscardEnable(false)
        rasterizer.polygonMode(VK_POLYGON_MODE_FILL)
        rasterizer.lineWidth(1f)
        rasterizer.cullMode(VK_CULL_MODE_BACK_BIT)
        rasterizer.frontFace(VK_FRONT_FACE_CLOCKWISE)
        rasterizer.depthBiasEnable(false)

        // Multisampling
        val multisampling = VkPipelineMultisampleStateCreateInfo.calloc()
        multisampling.sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
        multisampling.sampleShadingEnable(false)
        multisampling.rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)
        multisampling.minSampleShading(1f)

        // Color blending
        val colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc()
        colorBlendAttachment.colorWriteMask(VK_COLOR_COMPONENT_R_BIT or VK_COLOR_COMPONENT_G_BIT or VK_COLOR_COMPONENT_B_BIT or VK_COLOR_COMPONENT_A_BIT)
        colorBlendAttachment.blendEnable(false)

        /* Alpha blending:

            colorBlendAttachment.blendEnable = VK_TRUE;
            colorBlendAttachment.srcColorBlendFactor = VK_BLEND_FACTOR_SRC_ALPHA;
            colorBlendAttachment.dstColorBlendFactor = VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
            colorBlendAttachment.colorBlendOp = VK_BLEND_OP_ADD;
            colorBlendAttachment.srcAlphaBlendFactor = VK_BLEND_FACTOR_ONE;
            colorBlendAttachment.dstAlphaBlendFactor = VK_BLEND_FACTOR_ZERO;
            colorBlendAttachment.alphaBlendOp = VK_BLEND_OP_ADD;
         */
        val colorBlending = VkPipelineColorBlendStateCreateInfo.calloc()
        colorBlending.sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
        colorBlending.logicOpEnable(false)
        colorBlending.logicOp(VK_LOGIC_OP_COPY)
        colorBlending.pAttachments(VkPipelineColorBlendAttachmentState.calloc(1).put(colorBlendAttachment).flip())

        val pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc()
        pipelineLayoutInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)

        pipelineLayout = MemoryStack.stackPush().use {
            val pLayout = it.mallocLong(1)
            if(vkCreatePipelineLayout(logicalDevice, pipelineLayoutInfo, null, pLayout) != VK_SUCCESS)
                error("Failed to create pipeline layout")
            pLayout[0]
        }

        val pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1)
        pipelineInfo.sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
        pipelineInfo.pStages(shaderStages)
        pipelineInfo.pVertexInputState(vertexInputInfo)
        pipelineInfo.pInputAssemblyState(inputAssembly)
        pipelineInfo.pViewportState(viewportState)
        pipelineInfo.pRasterizationState(rasterizer)
        pipelineInfo.pMultisampleState(multisampling)
        pipelineInfo.pDepthStencilState(null)
        pipelineInfo.pColorBlendState(colorBlending)
        pipelineInfo.pDynamicState(null)

        pipelineInfo.layout(pipelineLayout)
        pipelineInfo.renderPass(renderPass)
        pipelineInfo.subpass(0)

        pipelineInfo.basePipelineHandle(VK_NULL_HANDLE)

        graphicsPipeline = MemoryStack.stackPush().use {
            val pPipeline = it.mallocLong(1)
            if(vkCreateGraphicsPipelines(logicalDevice, VK_NULL_HANDLE, pipelineInfo, null, pPipeline) != VK_SUCCESS)
                error("Failed to create graphics pipelin")
            pPipeline[0]
        }

        vkDestroyShaderModule(logicalDevice, vertModule, null)
        vkDestroyShaderModule(logicalDevice, fragModule, null)
    }

    private fun createShaderModule(code: ByteArray): VkShaderModule {
        val createInfo = VkShaderModuleCreateInfo.calloc()
        createInfo.sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
        val codeData = BufferUtils.createByteBuffer(code.size)
        codeData.put(code)
        codeData.flip()
        createInfo.pCode(codeData)

        val shaderModule: VkShaderModule = MemoryStack.stackPush().use {
            val pModule = it.mallocLong(1)
            if(vkCreateShaderModule(logicalDevice, createInfo, null, pModule) != VK_SUCCESS) {
                error("Failed to create shader module")
            }

            pModule[0]
        }
        return shaderModule
    }

    private fun createVulkanInstance(): VkInstance {
        if(enableValidationLayers)
            checkValidationLayers()
        val appInfo = VkApplicationInfo.calloc()
        appInfo.apiVersion(VK_MAKE_VERSION(1, 0, 2))
        appInfo.applicationVersion(VK_MAKE_VERSION(1, 0, 0))
        appInfo.engineVersion(VK_MAKE_VERSION(1, 0, 0))
        appInfo.pEngineName(memASCII("No engine"))
        appInfo.pApplicationName(memASCII("Vulkan triangle"))

        appInfo.sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)

        val createInfo = VkInstanceCreateInfo.calloc()
        createInfo.sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
        createInfo.pApplicationInfo(appInfo)

        checkExtensions()

        val extensions = getRequiredExtensions()
        createInfo.ppEnabledExtensionNames(extensions)

        //memFree(extensions)

        val handle = MemoryStack.stackPush().use {
            if(enableValidationLayers) {
                val layers = BufferUtils.createPointerBuffer(validationLayers.size)
                for(layer in validationLayers) {
                    layers.put(memUTF8(layer))
                }
                layers.flip()
                createInfo.ppEnabledLayerNames(layers)
            }
            val instancePointer = it.mallocPointer(1)
            val result = vkCreateInstance(createInfo, null, instancePointer)
            if(result != VK_SUCCESS)
                error("Could not create Vulkan instance")
            instancePointer[0]
        }
        //createInfo.free()
        //appInfo.free()
        return VkInstance(handle, createInfo)
    }

    private fun setupDebugCallback() {
        if(!enableValidationLayers)
            return
        val createInfo = VkDebugReportCallbackCreateInfoEXT.calloc()
        createInfo.sType(VK_STRUCTURE_TYPE_DEBUG_REPORT_CALLBACK_CREATE_INFO_EXT)
        createInfo.flags(VK_DEBUG_REPORT_ERROR_BIT_EXT or VK_DEBUG_REPORT_WARNING_BIT_EXT or VK_DEBUG_REPORT_DEBUG_BIT_EXT)
        createInfo.pfnCallback(DebugCallback)

        val callbackPointer = MemoryStack.stackMallocLong(1)
        if(vkCreateDebugReportCallbackEXT(vkInstance, createInfo, null, callbackPointer) != VK_SUCCESS) {
            error("Error while setting up debug callback")
        }

        val callbackHandle = callbackPointer[0]
        //createInfo.free()
    }

    private fun getRequiredExtensions(): PointerBuffer {
        val glfwExtensions = glfwGetRequiredInstanceExtensions()!!
        if(enableValidationLayers) {
            val extensions = BufferUtils.createPointerBuffer(glfwExtensions.capacity() + 1)
            extensions.put(glfwExtensions)
            extensions.put(memUTF8(VK_EXT_DEBUG_REPORT_EXTENSION_NAME))
            extensions.flip()
            return extensions
        }
        return glfwExtensions
    }

    private fun checkValidationLayers() {
        MemoryStack.stackPush().use {
            val countBuffer = it.mallocInt(1)
            vkEnumerateInstanceLayerProperties(countBuffer, null)
            val layers = VkLayerProperties.calloc(countBuffer[0])
            vkEnumerateInstanceLayerProperties(countBuffer, layers)

            for(layerName in validationLayers) {
                var found = false

                layers.forEach { layer ->
                    if(layer.layerNameString() == layerName) {
                        found = true
                        return@forEach
                    }
                }

                if(!found) {
                    error("Missing validation layer '$layerName'")
                }
            }
        }

        println("Found all validation layers")
    }

    private fun checkExtensions() {
        MemoryStack.stackPush().use {
            val countBuffer = it.mallocInt(1)
            vkEnumerateInstanceExtensionProperties(null as? ByteBuffer, countBuffer, null)
            val extensions = VkExtensionProperties.calloc(countBuffer[0])
            vkEnumerateInstanceExtensionProperties(null as? ByteBuffer, countBuffer, extensions)

            extensions.forEach { extension ->
                println("Found extension ${extension.extensionNameString()}")
            }
        }
    }

    private fun pickPhysicalDevice(): VkPhysicalDevice {
        val deviceCount = memAllocInt(1)
        vkEnumeratePhysicalDevices(vkInstance, deviceCount, null)
        val devicePointers = memAllocPointer(deviceCount[0])
        vkEnumeratePhysicalDevices(vkInstance, deviceCount, devicePointers)

        var graphicsCard: VkPhysicalDevice? = null
        for(i in 0 until deviceCount[0]) {
            val handle = devicePointers[i]
            val device = VkPhysicalDevice(handle, vkInstance)

            if(isDeviceSuitable(device)) {
                graphicsCard = device
                return device
            }
        }
        return graphicsCard ?: error("No suitable GPU for Vulkan")
    }

    private fun isDeviceSuitable(device: VkPhysicalDevice): Boolean {
        val indices = findQueueFamilies(device)
        if(!indices.isComplete)
            return false
        if(!checkDeviceExtensionSupport(device))
            return false
        val swapChainSupport = querySwapChainSupport(device)
        if(swapChainSupport.formats.isEmpty() || swapChainSupport.presentModes.isEmpty())
            return false

        return true
    }

    private fun chooseSwapSurfaceFormat(availableFormats: VkSurfaceFormatKHR.Buffer): VkSurfaceFormatKHR {
        if(availableFormats.capacity() == 1 && availableFormats[0].format() == VK_FORMAT_UNDEFINED) { // no preferred format
            val buffer = BufferUtils.createByteBuffer(2 * 4)
            buffer.putInt(VK_FORMAT_B8G8R8A8_UNORM)
            buffer.putInt(VK_COLOR_SPACE_SRGB_NONLINEAR_KHR)
            buffer.flip()
            return VkSurfaceFormatKHR(buffer)
        }

        availableFormats.forEach { format ->
            if(format.format() == VK_FORMAT_B8G8R8A8_UNORM && format.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                return format
            }
        }

        return availableFormats[0]
    }

    private fun chooseSwapPresentMode(presentModes: Array<VkPresentModeKHR>): VkPresentModeKHR {
        var bestMode = VK_PRESENT_MODE_FIFO_KHR

        for(mode in presentModes) {
            if(mode == VK_PRESENT_MODE_MAILBOX_KHR)
                return mode
            else if(mode == VK_PRESENT_MODE_IMMEDIATE_KHR)
                bestMode = mode
        }

        return bestMode
    }

    private fun chooseSwapExtent(capabilities: VkSurfaceCapabilitiesKHR): VkExtent2D {
        if((capabilities.currentExtent().width().toLong() and 0xFFFFFFFF) != (Integer.MAX_VALUE.toLong() and 0xFFFFFFFF)) {
            return capabilities.currentExtent()
        }

        val buffer = BufferUtils.createByteBuffer(2*4)
        buffer.putInt(WIDTH)
        buffer.putInt(HEIGHT)
        buffer.flip()
        val actualExtent = VkExtent2D(buffer)

        val w = maxOf(capabilities.minImageExtent().width(), minOf(capabilities.maxImageExtent().width(), actualExtent.width()))
        val h = maxOf(capabilities.minImageExtent().height(), minOf(capabilities.maxImageExtent().height(), actualExtent.height()))
        actualExtent.width(w)
        actualExtent.height(h)

        return actualExtent
    }

    private fun createSwapChain(details: SwapChainSupportDetails, surfaceFormat: VkSurfaceFormatKHR, extent: VkExtent2D, presentMode: VkPresentModeKHR): Pair<VkSwapchainKHR, Array<VkImage>> {
        var imageCount = details.capabilities.minImageCount() +1
        if(details.capabilities.maxImageCount() > 0 && imageCount > details.capabilities.maxImageCount()) { // check if upper limit
            imageCount = details.capabilities.maxImageCount()
        }

        val createInfo = VkSwapchainCreateInfoKHR.calloc()
        createInfo.sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
        createInfo.surface(surface)
        createInfo.minImageCount(imageCount)
        createInfo.imageFormat(surfaceFormat.format())
        createInfo.imageColorSpace(surfaceFormat.colorSpace())
        createInfo.imageExtent(extent)
        createInfo.imageArrayLayers(1) // not 1 only for 3D stereoscopic apps
        createInfo.imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)

        val indices = findQueueFamilies(physicalDevice)
        if(indices.graphicsFamily != indices.presentFamily) {
            createInfo.imageSharingMode(VK_SHARING_MODE_CONCURRENT)
            val familyIndices = BufferUtils.createIntBuffer(2).put(indices.graphicsFamily).put(indices.presentFamily)
            familyIndices.flip()
            createInfo.pQueueFamilyIndices(familyIndices)
        } else {
            createInfo.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
        }

        createInfo.preTransform(details.capabilities.currentTransform())

        createInfo.compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)

        createInfo.presentMode(presentMode)
        createInfo.clipped(true)
        createInfo.oldSwapchain(nullptr)

        val swapchain = MemoryStack.stackPush().use {
            val pSwapChain = it.mallocLong(1)
            if(vkCreateSwapchainKHR(logicalDevice, createInfo, null, pSwapChain) != VK_SUCCESS) {
                error("Failed to create swap chain")
            }

            pSwapChain[0]
        }

        val imageArray = MemoryStack.stackPush().use {
            val pImageCount = it.mallocInt(1)
            vkGetSwapchainImagesKHR(logicalDevice, swapchain, pImageCount, null)

            val images = it.mallocLong(pImageCount[0])
            vkGetSwapchainImagesKHR(logicalDevice, swapchain, pImageCount, images)

            Array<VkImage>(pImageCount[0]) { i -> images[i] }
        }


        return Pair(swapchain, imageArray)
    }

    private fun checkDeviceExtensionSupport(device: VkPhysicalDevice): Boolean {
        return MemoryStack.stackPush().use {
            val count = it.mallocInt(1)
            vkEnumerateDeviceExtensionProperties(device, null as? ByteBuffer, count, null)

            val availableExtensions = VkExtensionProperties.callocStack(count[0])
            vkEnumerateDeviceExtensionProperties(device, null as? ByteBuffer, count, availableExtensions)

            val requiredExtensions = mutableListOf(*deviceExtensions)
            availableExtensions.forEach {
                requiredExtensions -= it.extensionNameString()
            }

            if(requiredExtensions.isNotEmpty()) {
                println("Missing extensions:")
                for(required in requiredExtensions) {
                    println("\t- $required")
                }
            }
            requiredExtensions.isEmpty()
        }
    }

    private fun findQueueFamilies(device: VkPhysicalDevice): QueueFamilyIndices {
        val indices = QueueFamilyIndices()
        val count = memAllocInt(1)
        vkGetPhysicalDeviceQueueFamilyProperties(device, count, null)
        val families = VkQueueFamilyProperties.calloc(count[0])
        vkGetPhysicalDeviceQueueFamilyProperties(device, count, families)

        var i = 0
        families.forEach {  queueFamily ->
            if(queueFamily.queueCount() > 0 && queueFamily.queueFlags() and VK_QUEUE_GRAPHICS_BIT != 0) {
                indices.graphicsFamily = i
            }

            val presentSupport = MemoryStack.stackPush().use {
                val pSupport = it.mallocInt(1)
                vkGetPhysicalDeviceSurfaceSupportKHR(device, i, surface, pSupport)
                pSupport[0] == VK_TRUE
            }

            if(queueFamily.queueCount() > 0 && presentSupport) {
                indices.presentFamily = i
            }

            if(indices.isComplete)
                return@forEach
            i++
        }
        return indices
    }

    private fun createLogicalDevice(): VkDevice {
        val queueCreateInfo = VkDeviceQueueCreateInfo.calloc(2)
        val uniqueQueueFamilies = arrayOf(indices.graphicsFamily, indices.presentFamily)
        for((index, queueFamily) in uniqueQueueFamilies.withIndex()) {
            val info = queueCreateInfo[index]
            info.sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
            info.queueFamilyIndex(queueFamily)
            info.pQueuePriorities(MemoryStack.stackFloats(1f))
        }

        val createInfo = VkDeviceCreateInfo.calloc()
        createInfo.sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
        createInfo.pQueueCreateInfos(queueCreateInfo)

        val deviceFeatures = VkPhysicalDeviceFeatures.calloc()
        createInfo.pEnabledFeatures(deviceFeatures)
        val pExtensionNames = memAllocPointer(deviceExtensions.size)
        for(extension in deviceExtensions) {
            pExtensionNames.put(memUTF8(extension))
        }
        pExtensionNames.flip()
        createInfo.ppEnabledExtensionNames(pExtensionNames)

        val handle = MemoryStack.stackPush().use {
            if (enableValidationLayers) {

                val layers = BufferUtils.createPointerBuffer(validationLayers.size)
                for(layer in validationLayers) {
                    layers.put(memUTF8(layer))
                }
                layers.flip()
                createInfo.ppEnabledLayerNames(layers)
            }
            val pDevice = it.mallocPointer(1)
            if(vkCreateDevice(physicalDevice, createInfo, null, pDevice) != VK_SUCCESS)
                error("Failed to create logical device")
            pDevice[0]
        }

        return VkDevice(handle, physicalDevice, createInfo)
    }

    private fun createGraphicsQueue() = createQueue(indices.graphicsFamily)

    private fun createPresentQueue() = createQueue(indices.presentFamily)

    private fun createQueue(index: Int): VkQueue {
        val handle = MemoryStack.stackPush().use {
            val pQueue = it.mallocPointer(1)
            vkGetDeviceQueue(logicalDevice, index, 0, pQueue)
            pQueue[0]
        }

        return VkQueue(handle, logicalDevice)
    }

    private fun createSurface(): Long {
        return MemoryStack.stackPush().use {
            val pSurface = it.mallocLong(1)
            glfwCreateWindowSurface(vkInstance, windowPointer, null, pSurface)
            pSurface[0]
        }
    }

    private fun querySwapChainSupport(device: VkPhysicalDevice): SwapChainSupportDetails {
        val capabilities = VkSurfaceCapabilitiesKHR.calloc()
        vkGetPhysicalDeviceSurfaceCapabilitiesKHR(device, surface, capabilities)

        val details = MemoryStack.stackPush().use {
            val formatCount = it.mallocInt(1)
            vkGetPhysicalDeviceSurfaceFormatsKHR(device, surface, formatCount, null)

            val pFormats = VkSurfaceFormatKHR.calloc(formatCount[0])
            vkGetPhysicalDeviceSurfaceFormatsKHR(device, surface, formatCount, pFormats)

            val presentModeCount = it.mallocInt(1)
            vkGetPhysicalDeviceSurfacePresentModesKHR(device, surface, presentModeCount, null)

            val modes = it.mallocInt(presentModeCount[0])
            vkGetPhysicalDeviceSurfacePresentModesKHR(device, surface, presentModeCount, modes)

            val modeArray = Array<VkPresentModeKHR>(presentModeCount[0]) { i -> modes[i] }

            SwapChainSupportDetails(capabilities, pFormats, modeArray)
        }
        return details
    }

    private fun createImageViews(): Array<VkImageView> {
        val result = Array<VkImageView>(swapchainImages.size) { -1 }
        swapchainImages.forEachIndexed { index, image ->
            val createInfo = VkImageViewCreateInfo.calloc()
            createInfo.sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
            createInfo.image(image)
            createInfo.viewType(VK_IMAGE_VIEW_TYPE_2D)
            createInfo.format(swapchainImageFormat)

            createInfo.components()
                .r(VK_COMPONENT_SWIZZLE_IDENTITY)
                .g(VK_COMPONENT_SWIZZLE_IDENTITY)
                .b(VK_COMPONENT_SWIZZLE_IDENTITY)
                .a(VK_COMPONENT_SWIZZLE_IDENTITY)

            createInfo.subresourceRange()
                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT) // color target
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1)

            val handle = MemoryStack.stackPush().use {
                val pView = it.mallocLong(1)
                vkCreateImageView(logicalDevice, createInfo, null, pView)
                pView[0]
            }
            result[index] = handle
        }

        return result
    }

    object DebugCallback: VkDebugReportCallbackEXT() {
        override fun invoke(flags: Int, objectType: Int, `object`: Long, location: Long, messageCode: Int, pLayerPrefix: Long, pMessage: Long, pUserData: Long): Int {
            val msg = MemoryUtil.memASCII(pMessage)
            val layer = memASCII(pLayerPrefix)
            println("Validation layer ($layer): $msg")
            println("\t${VkDebugReportCallbackEXT.getString(pMessage)}")
            return VK_FALSE
        }

    }
}