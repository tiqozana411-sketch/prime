package com.prime.remote

import timber.log.Timber
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import java.security.MessageDigest
import java.util.Base64

/**
 * WebSocket服务器
 * 用于远程控制通信
 */
class RemoteWebSocketServer(
    private val port: Int = 8765
) {
    
    private var serverSocket: ServerSocket? = null
    private val clients = ConcurrentHashMap<String, Socket>()
    private val isRunning = AtomicBoolean(false)
    private var serverJob: Job? = null
    
    private var onCommandReceived: ((String) -> Unit)? = null
    
    /**
     * 启动服务器
     */
    fun start(onCommand: (String) -> Unit) {
        if (isRunning.get()) {
            Timber.w("⚠️ 服务器已在运行")
            return
        }
        
        this.onCommandReceived = onCommand
        isRunning.set(true)
        
        serverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(port)
                Timber.i("🌐 WebSocket服务器启动: 端口$port")
                
                while (isRunning.get()) {
                    try {
                        val client = serverSocket?.accept()
                        if (client != null) {
                            handleClient(client)
                        }
                    } catch (e: Exception) {
                        if (isRunning.get()) {
                            Timber.e(e, "❌ 接受连接失败")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "❌ 服务器启动失败")
            }
        }
    }
    
/**
     * 处理客户端连接
     */
    private fun handleClient(client: Socket) {
        CoroutineScope(Dispatchers.IO).launch {
            val clientId = "${client.inetAddress.hostAddress}:${client.port}"
            clients[clientId] = client
            
            Timber.i("✅ 客户端连接: $clientId")
            
            try {
                val inputStream = client.getInputStream()
                val bufferedReader = inputStream.bufferedReader()
                var isWebSocketMode = false
                
                while (isRunning.get() && !client.isClosed) {
                    if (!isWebSocketMode) {
                        // HTTP握手阶段：读取文本行
                        val line = bufferedReader.readLine() ?: break
                        
                        if (line.startsWith("GET")) {
                            handleWebSocketHandshake(client, line, bufferedReader)
                            isWebSocketMode = true
                            Timber.d("🔄 切换到WebSocket帧模式")
                            continue
                        }
} else {
                        // WebSocket帧模式：解析二进制帧
                        val frame = decodeWebSocketFrame(inputStream) ?: break
                        
                        if (frame.isNotEmpty()) {
                            val message = String(frame, Charsets.UTF_8)
                            Timber.d("📨 收到消息: $message")
                            onCommandReceived?.invoke(message)
                        } else {
                            // 空帧可能是Ping，回复Pong
                            sendPongFrame(client)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "❌ 客户端通信错误: $clientId")
            } finally {
                clients.remove(clientId)
                client.close()
                Timber.i("❌ 客户端断开: $clientId")
            }
        }
    }
/**
     * 处理WebSocket握手（RFC 6455标准）
     */
    private fun handleWebSocketHandshake(client: Socket, request: String, reader: java.io.BufferedReader) {
        try {
            val headers = mutableMapOf<String, String>()
            
            // 读取所有HTTP头
            var line = reader.readLine()
            while (!line.isNullOrEmpty()) {
                if (line.contains(":")) {
                    val parts = line.split(":", limit = 2)
                    headers[parts[0].trim().lowercase()] = parts[1].trim()
                }
                line = reader.readLine()
            }
            
            // 提取Sec-WebSocket-Key
            val webSocketKey = headers["sec-websocket-key"]
            if (webSocketKey == null) {
                Timber.w("⚠️ 缺少Sec-WebSocket-Key")
                client.close()
                return
            }
            
            // 计算Sec-WebSocket-Accept（RFC 6455标准）
            val magicString = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
            val sha1 = MessageDigest.getInstance("SHA-1")
            val hash = sha1.digest((webSocketKey + magicString).toByteArray())
            val acceptKey = Base64.getEncoder().encodeToString(hash)
            
            // 发送握手响应
            val response = """
HTTP/1.1 101 Switching Protocols
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Accept: $acceptKey

""".replace("\n", "\r\n")
            
            client.getOutputStream().write(response.toByteArray())
            client.getOutputStream().flush()
            
            Timber.i("✅ WebSocket握手成功")
        } catch (e: Exception) {
            Timber.e(e, "❌ WebSocket握手失败")
            client.close()
        }
    }
    
    /**
     * 解码WebSocket帧（RFC 6455标准）
     * 客户端发送的帧必须有mask
     */
    private fun decodeWebSocketFrame(input: java.io.InputStream): ByteArray? {
        try {
            // Byte 0: FIN + Opcode
            val byte0 = input.read()
            if (byte0 == -1) return null
            
            val fin = (byte0 and 0x80) != 0
            val opcode = byte0 and 0x0F
            
            // Opcode 0x8 = 关闭帧
            if (opcode == 0x8) {
                Timber.d("📪 收到关闭帧")
                return null
            }
            
// Opcode 0x9 = Ping帧
            if (opcode == 0x9) {
                Timber.d("🏓 收到Ping帧，回复Pong")
                // Ping帧不需要回复，直接返回空（Pong在外层处理）
                return ByteArray(0)
            }
            
            // Byte 1: MASK + Payload Length
            val byte1 = input.read()
            if (byte1 == -1) return null
            
            val masked = (byte1 and 0x80) != 0
            var payloadLength = (byte1 and 0x7F).toLong()
            
            // 扩展长度
            if (payloadLength == 126L) {
                val len1 = input.read()
                val len2 = input.read()
                if (len1 == -1 || len2 == -1) return null
                payloadLength = ((len1 shl 8) or len2).toLong()
            } else if (payloadLength == 127L) {
                var len = 0L
                for (i in 0 until 8) {
                    val b = input.read()
                    if (b == -1) return null
                    len = (len shl 8) or b.toLong()
                }
                payloadLength = len
            }
            
            // 读取Mask Key（客户端必须mask）
            val maskKey = if (masked) {
                ByteArray(4) { input.read().toByte() }
            } else {
                Timber.w("⚠️ 客户端帧未mask，违反RFC 6455")
                return null
            }
            
            // 读取Payload
            if (payloadLength > Int.MAX_VALUE) {
                Timber.e("❌ Payload过大: $payloadLength")
                return null
            }
            
            val payload = ByteArray(payloadLength.toInt())
            var totalRead = 0
            while (totalRead < payloadLength) {
                val read = input.read(payload, totalRead, (payloadLength - totalRead).toInt())
                if (read == -1) return null
                totalRead += read
            }
            
            // 解mask
            for (i in payload.indices) {
                payload[i] = (payload[i].toInt() xor maskKey[i % 4].toInt()).toByte()
            }
            
            return payload
            
        } catch (e: Exception) {
            Timber.e(e, "❌ 解码WebSocket帧失败")
            return null
        }
    }
    
    /**
     * 广播帧数据到所有客户端（WebSocket帧格式）
     */
    fun broadcastFrame(frameData: ByteArray) {
        clients.values.forEach { client ->
            try {
                if (!client.isClosed) {
                    val frame = encodeWebSocketFrame(frameData)
                    client.getOutputStream().write(frame)
                    client.getOutputStream().flush()
                }
            } catch (e: Exception) {
                Timber.e(e, "❌ 发送帧失败")
            }
        }
    }
    
    /**
     * 编码WebSocket帧（RFC 6455标准）
     * 服务端发送的帧不需要mask
     */
    private fun encodeWebSocketFrame(payload: ByteArray): ByteArray {
        val payloadLength = payload.size
        val frame = mutableListOf<Byte>()
        
        // Byte 0: FIN=1, RSV=0, Opcode=0x1(文本帧)
        frame.add(0x81.toByte())
        
        // Byte 1: MASK=0, Payload Length
        when {
            payloadLength <= 125 -> {
                frame.add(payloadLength.toByte())
            }
            payloadLength <= 65535 -> {
                frame.add(126.toByte())
                frame.add((payloadLength shr 8).toByte())
                frame.add((payloadLength and 0xFF).toByte())
            }
            else -> {
                frame.add(127.toByte())
                for (i in 7 downTo 0) {
                    frame.add((payloadLength shr (i * 8)).toByte())
                }
            }
        }
        
        // Payload数据
        frame.addAll(payload.toList())
        
        return frame.toByteArray()
    }
    
    /**
     * 发送消息到指定客户端（WebSocket帧格式）
     */
    fun sendMessage(clientId: String, message: String): Boolean {
        val client = clients[clientId] ?: return false
        
        return try {
            val frame = encodeWebSocketFrame(message.toByteArray())
            client.getOutputStream().write(frame)
            client.getOutputStream().flush()
            true
        } catch (e: Exception) {
            Timber.e(e, "❌ 发送消息失败")
            false
        }
    }
    
    /**
     * 停止服务器
     */
    fun stop() {
        isRunning.set(false)
        
        clients.values.forEach { it.close() }
        clients.clear()
        
        serverSocket?.close()
        serverSocket = null
        
        serverJob?.cancel()
        serverJob = null
        
        Timber.i("🌐 WebSocket服务器已停止")
    }
    
/**
     * 发送Pong帧（回复Ping）
     */
    private fun sendPongFrame(client: Socket) {
        try {
            // Pong帧：FIN=1, Opcode=0xA, 无payload
            val pongFrame = byteArrayOf(0x8A.toByte(), 0x00)
            client.getOutputStream().write(pongFrame)
            client.getOutputStream().flush()
        } catch (e: Exception) {
            Timber.e(e, "❌ 发送Pong帧失败")
        }
    }
    
    /**
     * 获取连接的客户端数量
     */
    fun getClientCount() = clients.size
}