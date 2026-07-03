package gama.ui.display.webrtc;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

public class WebRTCSessionManager {

	private static final int HTTP_PORT = 8082;

	private static WebRTCSessionManager instance;

	private HttpServer httpServer;
	private boolean running;

	private final ConcurrentHashMap<String, DisplayStream> displays = new ConcurrentHashMap<>();

	private WebRTCSessionManager() {}

	public static synchronized WebRTCSessionManager getInstance() {
		if (instance == null) {
			instance = new WebRTCSessionManager();
			instance.start();
		}
		return instance;
	}

	private void start() {
		if (running) return;
		running = true;
		startHttp();
	}

	Set<String> getDisplayIds() {
		return displays.keySet();
	}

	public void broadcastFrame(final String displayId, final byte[] rgba, final int width, final int height) {
		if (displayId == null || rgba == null || width <= 0 || height <= 0) return;
		final DisplayStream ds = displays.computeIfAbsent(displayId, k -> new DisplayStream());
		final ThreadLocal<JpegEncoder> enc = ds.encoder;
		try {
			final JpegEncoder e = enc.get();
			e.ensureSize(width, height);
			final int[] data = ((DataBufferInt) e.image.getRaster().getDataBuffer()).getData();
			for (int y = 0; y < height; y++) {
				final int srcY = height - 1 - y;
				final int rowBase = srcY * width;
				final int dstBase = y * width;
				for (int x = 0; x < width; x++) {
					final int off = (rowBase + x) * 4;
					final int r = rgba[off] & 0xFF;
					final int g = rgba[off + 1] & 0xFF;
					final int b = rgba[off + 2] & 0xFF;
					data[dstBase + x] = 0xFF000000 | r << 16 | g << 8 | b;
				}
			}
			final ByteArrayOutputStream baos = new ByteArrayOutputStream(512 * 1024);
			try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
				e.writer.setOutput(ios);
				e.writer.write(e.image);
			}
			ds.setJpeg(baos.toByteArray());
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

	public void removeDisplay(final String displayId) {
		displays.remove(displayId);
	}

	private void startHttp() {
		httpServer = new HttpServer(HTTP_PORT);
		httpServer.start();
	}

	public void dispose() {
		running = false;
		displays.clear();
		if (httpServer != null) {
			httpServer.stopServer();
			httpServer = null;
		}
	}

	private static class DisplayStream {
		final ThreadLocal<JpegEncoder> encoder = ThreadLocal.withInitial(JpegEncoder::new);
		volatile byte[] currentJpeg;
		final Object frameLock = new Object();
		int frameSeq;

		void setJpeg(final byte[] jpeg) {
			synchronized (frameLock) {
				currentJpeg = jpeg;
				frameSeq++;
			}
		}
	}

	private static class JpegEncoder {
		BufferedImage image;
		ImageWriter writer;
		ImageWriteParam params;

		void ensureSize(final int w, final int h) {
			if (image != null && image.getWidth() == w && image.getHeight() == h) return;
			image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
			final Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
			if (!writers.hasNext()) {
				throw new RuntimeException("No JPEG ImageWriter available");
			}
			writer = writers.next();
			params = writer.getDefaultWriteParam();
			params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			params.setCompressionQuality(0.85f);
		}
	}

	private class HttpServer extends Thread {
		private final int port;
		private volatile boolean active = true;
		private ServerSocket serverSocket;

		HttpServer(final int port) {
			super("Gama-HTTP");
			this.port = port;
			setDaemon(true);
		}

		@Override
		public void run() {
			try {
				serverSocket = new ServerSocket(port);
				while (active) {
					try {
						final Socket client = serverSocket.accept();
						new Thread(() -> handle(client), "Gama-HTTP").start();
					} catch (final IOException e) {
						if (active) e.printStackTrace();
					}
				}
			} catch (final IOException e) {
				if (active) e.printStackTrace();
			}
		}

		private void handle(final Socket client) {
			try (client) {
				final InputStream in = client.getInputStream();
				final byte[] buf = new byte[4096];
				final int read = in.read(buf);
				if (read < 0) return;
				final String request = new String(buf, 0, read);
				if (!request.startsWith("GET")) return;

				final String path = extractPath(request);
				final String etag = extractHeader(request, "If-None-Match");

				if (path.equals("/api/displays")) {
					serveDisplayList(client);
				} else if (path.startsWith("/frame/")) {
					final String id = path.substring("/frame/".length());
					serveFrame(client, id, etag);
				} else {
					servePage(client);
				}
			} catch (final Exception e) {
				if (active) e.printStackTrace();
			}
		}

		private String extractPath(final String request) {
			final int s = request.indexOf(' ') + 1;
			final int e = request.indexOf(' ', s);
			if (s <= 0 || e <= s) return "/";
			String path = request.substring(s, e);
			if (path.equals("/")) path = "/index.html";
			return path;
		}

		private String extractHeader(final String request, final String name) {
			final int i = request.indexOf("\r\n" + name + ": ");
			if (i < 0) return null;
			int start = i + name.length() + 4;
			int end = request.indexOf("\r\n", start);
			return end > start ? request.substring(start, end).trim() : null;
		}

		private String cors() { return "Access-Control-Allow-Origin: *\r\n"; }

		private void serveDisplayList(final Socket client) throws IOException {
			final StringBuilder json = new StringBuilder("[");
			boolean first = true;
			for (final String id : displays.keySet()) {
				if (!first) json.append(",");
				json.append("\"").append(id).append("\"");
				first = false;
			}
			json.append("]");
			final byte[] body = json.toString().getBytes();
			final String header = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n"
				+ cors() + "Content-Length: " + body.length + "\r\nConnection: keep-alive\r\n\r\n";
			final OutputStream out = client.getOutputStream();
			out.write(header.getBytes());
			out.write(body);
			out.flush();
		}

		private void servePage(final Socket client) throws IOException {
			final byte[] body = loadHtml();
			final String header = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n"
				+ cors() + "Content-Length: " + body.length + "\r\nConnection: keep-alive\r\n\r\n";
			final OutputStream out = client.getOutputStream();
			out.write(header.getBytes());
			out.write(body);
			out.flush();
		}

		private void serveFrame(final Socket client, final String displayId, final String ifNoneMatch) throws IOException {
			final DisplayStream ds = displays.get(displayId);
			if (ds == null) {
				final byte[] body = "Unknown display".getBytes();
				final String header = "HTTP/1.1 404 Not Found\r\n" + cors()
					+ "Content-Length: " + body.length + "\r\nConnection: keep-alive\r\n\r\n";
				final OutputStream out = client.getOutputStream();
				out.write(header.getBytes());
				out.write(body);
				out.flush();
				return;
			}

			final byte[] jpeg = ds.currentJpeg;
			final int seq = ds.frameSeq;
			final String tag = "\"" + seq + "\"";

			if (jpeg == null) {
				final String header = "HTTP/1.1 204 No Content\r\n" + cors()
					+ "Content-Length: 0\r\nConnection: keep-alive\r\n\r\n";
				client.getOutputStream().write(header.getBytes());
				client.getOutputStream().flush();
				return;
			}

			if (tag.equals(ifNoneMatch)) {
				final String header = "HTTP/1.1 304 Not Modified\r\n" + cors()
					+ "ETag: " + tag + "\r\nConnection: keep-alive\r\n\r\n";
				client.getOutputStream().write(header.getBytes());
				client.getOutputStream().flush();
				return;
			}

			final OutputStream out = client.getOutputStream();
			final String header = "HTTP/1.1 200 OK\r\n" + cors()
				+ "Content-Type: image/jpeg\r\n"
				+ "Content-Length: " + jpeg.length + "\r\n"
				+ "ETag: " + tag + "\r\n"
				+ "Cache-Control: no-cache\r\n"
				+ "Connection: keep-alive\r\n\r\n";
			out.write(header.getBytes());
			out.write(jpeg);
			out.flush();
		}

		void stopServer() {
			active = false;
			try { if (serverSocket != null) serverSocket.close(); } catch (final IOException e) {}
		}
	}

	private static byte[] loadHtml() {
		try (InputStream is = WebRTCSessionManager.class.getResourceAsStream("/web/viewer.html")) {
			if (is == null) {
				return "<html><body><h1>Viewer missing in /web/viewer.html</h1></body></html>".getBytes();
			}
			return is.readAllBytes();
		} catch (final IOException e) {
			return ("<html><body><h1>Error loading viewer</h1><pre>" + e.getMessage() + "</pre></body></html>").getBytes();
		}
	}
}
