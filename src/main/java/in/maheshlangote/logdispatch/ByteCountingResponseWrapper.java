package in.maheshlangote.logdispatch;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A lightweight HttpServletResponseWrapper that tracks total response payload bytes
 * streaming out to the client with zero memory storage.
 */
public class ByteCountingResponseWrapper extends HttpServletResponseWrapper {

    private final AtomicLong byteCount = new AtomicLong(0);
    private ServletOutputStream outputStream;
    private PrintWriter printWriter;

    public ByteCountingResponseWrapper(HttpServletResponse response) {
        super(response);
    }

    public long getByteCount() {
        return byteCount.get();
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (printWriter != null) {
            throw new IllegalStateException("getWriter() has already been called for this response");
        }
        if (outputStream == null) {
            outputStream = new ByteCountingServletOutputStream(super.getOutputStream(), byteCount);
        }
        return outputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (outputStream != null) {
            throw new IllegalStateException("getOutputStream() has already been called for this response");
        }
        if (printWriter == null) {
            ByteCountingServletOutputStream countingStream = new ByteCountingServletOutputStream(super.getOutputStream(), byteCount);
            String encoding = getCharacterEncoding();
            OutputStreamWriter writer = (encoding != null)
                    ? new OutputStreamWriter(countingStream, encoding)
                    : new OutputStreamWriter(countingStream);
            printWriter = new PrintWriter(writer);
        }
        return printWriter;
    }

    @Override
    public void flushBuffer() throws IOException {
        if (printWriter != null) {
            printWriter.flush();
        }
        if (outputStream != null) {
            outputStream.flush();
        }
        super.flushBuffer();
    }

    private static class ByteCountingServletOutputStream extends ServletOutputStream {
        private final ServletOutputStream target;
        private final AtomicLong counter;

        public ByteCountingServletOutputStream(ServletOutputStream target, AtomicLong counter) {
            this.target = target;
            this.counter = counter;
        }

        @Override
        public boolean isReady() {
            return target.isReady();
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            target.setWriteListener(writeListener);
        }

        @Override
        public void write(int b) throws IOException {
            target.write(b);
            counter.incrementAndGet();
        }

        @Override
        public void write(byte[] b) throws IOException {
            target.write(b);
            if (b != null) {
                counter.addAndGet(b.length);
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            target.write(b, off, len);
            if (len > 0) {
                counter.addAndGet(len);
            }
        }

        @Override
        public void flush() throws IOException {
            target.flush();
        }

        @Override
        public void close() throws IOException {
            target.close();
        }
    }
}
