package org.jglrxavpok.vulkan

import org.lwjgl.system.Struct
import org.lwjgl.system.StructBuffer

fun <T: Struct, SELF: StructBuffer<T, SELF>> StructBuffer<T, SELF>.isEmpty() = capacity() == 0