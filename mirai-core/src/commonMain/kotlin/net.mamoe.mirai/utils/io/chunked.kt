/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

package net.mamoe.mirai.utils.io

import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.io.InputStream
import kotlinx.io.core.ByteReadPacket
import kotlinx.io.core.Input
import kotlinx.io.pool.useInstance
import net.mamoe.mirai.utils.MiraiInternalAPI


/**
 * 由 [chunkedFlow] 分割得到的区块
 */
class ChunkedInput(
    /**
     * 区块的数据.
     * 由 [ByteArrayPool] 缓存并管理, 只可在 [Flow.collect] 中访问.
     * 它的大小由 [ByteArrayPool.BUFFER_SIZE] 决定, 而有效（有数据）的大小由 [bufferSize] 决定.
     *
     * **注意**: 不要将他带出 [Flow.collect] 作用域, 否则将造成内存泄露
     */
    val buffer: ByteArray,
    internal var size: Int
) {
    /**
     * [buffer] 的有效大小
     */
    val bufferSize: Int get() = size
}

/**
 * 创建将 [ByteReadPacket] 以固定大小分割的 [Sequence].
 *
 * 对于一个 1000 长度的 [ByteReadPacket] 和参数 [sizePerPacket] = 300, 将会产生含四个元素的 [Sequence],
 * 其长度分别为: 300, 300, 300, 100.
 *
 * 若 [ByteReadPacket.remaining] 小于 [sizePerPacket], 将会返回唯一元素 [this] 的 [Sequence]
 */
@UseExperimental(MiraiInternalAPI::class)
fun ByteReadPacket.chunkedFlow(sizePerPacket: Int): Flow<ChunkedInput> {
    ByteArrayPool.checkBufferSize(sizePerPacket)
    if (this.remaining <= sizePerPacket.toLong()) {
        ByteArrayPool.useInstance { buffer ->
            return flowOf(ChunkedInput(buffer, this.readAvailable(buffer, 0, sizePerPacket)))
        }
    }
    return flow {
        ByteArrayPool.useInstance { buffer ->
            val chunkedInput = ChunkedInput(buffer, 0)
            do {
                chunkedInput.size = this@chunkedFlow.readAvailable(buffer, 0, sizePerPacket)
                emit(chunkedInput)
            } while (this@chunkedFlow.isNotEmpty)
        }
    }
}

/**
 * 创建将 [ByteReadChannel] 以固定大小分割的 [Sequence].
 *
 * 对于一个 1000 长度的 [ByteReadChannel] 和参数 [sizePerPacket] = 300, 将会产生含四个元素的 [Sequence],
 * 其长度分别为: 300, 300, 300, 100.
 */
@UseExperimental(MiraiInternalAPI::class)
fun ByteReadChannel.chunkedFlow(sizePerPacket: Int): Flow<ChunkedInput> {
    ByteArrayPool.checkBufferSize(sizePerPacket)
    if (this.isClosedForRead) {
        return flowOf()
    }
    return flow {
        ByteArrayPool.useInstance { buffer ->
            val chunkedInput = ChunkedInput(buffer, 0)
            do {
                chunkedInput.size = this@chunkedFlow.readAvailable(buffer, 0, sizePerPacket)
                emit(chunkedInput)
            } while (!this@chunkedFlow.isClosedForRead)
        }
    }
}


/**
 * 创建将 [Input] 以固定大小分割的 [Sequence].
 *
 * 对于一个 1000 长度的 [Input] 和参数 [sizePerPacket] = 300, 将会产生含四个元素的 [Sequence],
 * 其长度分别为: 300, 300, 300, 100.
 */
@UseExperimental(MiraiInternalAPI::class, ExperimentalCoroutinesApi::class)
internal fun Input.chunkedFlow(sizePerPacket: Int): Flow<ChunkedInput> {
    ByteArrayPool.checkBufferSize(sizePerPacket)

    if (this.endOfInput) {
        return flowOf()
    }

    return flow {
        ByteArrayPool.useInstance { buffer ->
            val chunkedInput = ChunkedInput(buffer, 0)
            while (!this@chunkedFlow.endOfInput) {
                chunkedInput.size = this@chunkedFlow.readAvailable(buffer, 0, sizePerPacket)
                emit(chunkedInput)
            }
        }
    }
}

/**
 * 创建将 [ByteReadPacket] 以固定大小分割的 [Sequence].
 *
 * 对于一个 1000 长度的 [ByteReadPacket] 和参数 [sizePerPacket] = 300, 将会产生含四个元素的 [Sequence],
 * 其长度分别为: 300, 300, 300, 100.
 *
 * 若 [ByteReadPacket.remaining] 小于 [sizePerPacket], 将会返回唯一元素 [this] 的 [Sequence]
 */
@UseExperimental(MiraiInternalAPI::class, ExperimentalCoroutinesApi::class)
internal fun InputStream.chunkedFlow(sizePerPacket: Int): Flow<ChunkedInput> {
    ByteArrayPool.checkBufferSize(sizePerPacket)

    return flow {
        ByteArrayPool.useInstance { buffer ->
            val chunkedInput = ChunkedInput(buffer, 0)
            while (this@chunkedFlow.available() != 0) {
                chunkedInput.size = this@chunkedFlow.read(buffer, 0, sizePerPacket)
                emit(chunkedInput)
            }
        }
    }
}