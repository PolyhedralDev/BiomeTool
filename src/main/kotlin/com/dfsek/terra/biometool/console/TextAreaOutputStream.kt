package com.dfsek.terra.biometool.console

import javafx.scene.control.TextArea
import java.io.OutputStream

class TextAreaOutputStream(
    private val textArea: TextArea
                          ) : OutputStream() {
    private val buffer: StringBuilder = StringBuilder()
    
    override fun write(byte: Int) {
        writeToBuffer(byte.toChar().toString())
    }
    
    override fun write(bytes: ByteArray) {
        writeToBuffer(bytes.decodeToString())
    }
    
    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        writeToBuffer(bytes.decodeToString(startIndex = offset, endIndex = offset + length))
    }
    
    private fun writeToBuffer(string: String) {
        buffer.append(string)
        
        if (string.contains('\n'))
            writeToTextArea()
    }
    
    private fun writeToTextArea() {
        textArea.appendText(buffer.toString())
        
        buffer.clear()
    }
}