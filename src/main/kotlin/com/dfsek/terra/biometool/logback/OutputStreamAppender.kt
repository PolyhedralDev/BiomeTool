package com.dfsek.terra.biometool.logback

import ch.qos.logback.core.OutputStreamAppender
import java.io.FilterOutputStream
import java.io.OutputStream
import org.apache.commons.io.output.NullOutputStream

class OutputStreamAppender<E> : OutputStreamAppender<E>() {
    override fun start() {
        this.outputStream = delegatingOutputStream
        super.start()
    }
    
    companion object {
        var outputStream: OutputStream
            get() = delegatingOutputStream.out
            set(value) {
                delegatingOutputStream.out = value
            }
        
        private val delegatingOutputStream = DelegatingOutputStream()
    }
    
    internal class DelegatingOutputStream : FilterOutputStream(NullOutputStream()) {
        var out: OutputStream
            get() = super.out
            set(value) {
                super.out = value
            }
        
        override fun flush() {
            out.flush()
        }
        
        override fun write(b: Int) {
            out.write(b)
        }
        
        override fun write(b: ByteArray) {
            out.write(b)
        }
        
        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
        }
    }
}