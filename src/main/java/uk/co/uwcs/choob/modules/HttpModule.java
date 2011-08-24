package uk.co.uwcs.choob.modules;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;

/**
 *
 * Totally taken from
 * http://code.google.com/p/and-bookworm/source/browse/trunk/src
 * /com/totsp/bookworm/data/HttpHelper.java
 *
 * NB: original code under Apache License
 *
 * Apache HttpClient helper class for performing HTTP requests.
 *
 */
public class HttpModule {

	private static final String CONTENT_TYPE = "Content-Type";
	private static final int POST_TYPE = 1;
	private static final int GET_TYPE = 2;
	private static final String GZIP = "gzip";
	private static final String ACCEPT_ENCODING = "Accept-Encoding";

	public static final String MIME_FORM_ENCODED = "application/x-www-form-urlencoded";
	public static final String MIME_TEXT_PLAIN = "text/plain";
	public static final String HTTP_RESPONSE = "HTTP_RESPONSE";
	public static final String HTTP_RESPONSE_ERROR = "HTTP_RESPONSE_ERROR";

	// Establish client once, as static field with static setup block.
	// (This is a best practice in HttpClient docs - but will leave reference
	// until *process* stopped on Android.)
	private static final DefaultHttpClient client;
	static {
		HttpParams params = new BasicHttpParams();
		params.setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
		params.setParameter(CoreProtocolPNames.HTTP_CONTENT_CHARSET, HTTP.UTF_8);
		params.setParameter(CoreProtocolPNames.USER_AGENT, "Apache-HttpClient/Android");
		params.setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 15000);
		params.setParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false);
		SchemeRegistry schemeRegistry = new SchemeRegistry();
		schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
		schemeRegistry.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));
		ThreadSafeClientConnManager cm = new ThreadSafeClientConnManager(params, schemeRegistry);
		client = new DefaultHttpClient(cm, params);
		// add gzip decompressor to handle gzipped content in responses
		// (default we *do* always send accept encoding gzip header in request)
		HttpModule.client.addResponseInterceptor(new HttpResponseInterceptor() {
			@Override
			public void process(final HttpResponse response, final HttpContext context) throws HttpException, IOException {
				HttpEntity entity = response.getEntity();
				Header contentEncodingHeader = entity.getContentEncoding();
				if (contentEncodingHeader != null) {
					HeaderElement[] codecs = contentEncodingHeader.getElements();
					for (int i = 0; i < codecs.length; i++) {
						if (codecs[i].getName().equalsIgnoreCase(HttpModule.GZIP)) {
							response.setEntity(new GzipDecompressingEntity(response.getEntity()));
							return;
						}
					}
				}
			}
		});
	}

	private final ResponseHandler<String> responseHandler;

	/**
	 * Constructor.
	 *
	 */
	public HttpModule() {
		responseHandler = new BasicResponseHandler();
	}

	/**
	 * Perform a simple HTTP GET operation.
	 *
	 */
	public String performGet(final String url) {
		return performRequest(null, url, null, null, null, null, HttpModule.GET_TYPE);
	}

	/**
	 * Perform an HTTP GET operation with user/pass and headers.
	 *
	 */
	public String performGet(final String url, final String user, final String pass, final Map<String, String> additionalHeaders) {
		return performRequest(null, url, user, pass, additionalHeaders, null, HttpModule.GET_TYPE);
	}

	/**
	 * Perform a simplified HTTP POST operation.
	 *
	 */
	public String performPost(final String url, final Map<String, String> params) {
		return performRequest(HttpModule.MIME_FORM_ENCODED, url, null, null, null, params, HttpModule.POST_TYPE);
	}

	/**
	 * Perform an HTTP POST operation with user/pass, headers, request
	 * parameters, and a default content-type of
	 * "application/x-www-form-urlencoded."
	 *
	 */
	public String performPost(final String url, final String user, final String pass, final Map<String, String> additionalHeaders,
			final Map<String, String> params) {
		return performRequest(HttpModule.MIME_FORM_ENCODED, url, user, pass, additionalHeaders, params, HttpModule.POST_TYPE);
	}

	/**
	 * Perform an HTTP POST operation with flexible parameters (the
	 * complicated/flexible version of the method).
	 *
	 */
	public String performPost(final String contentType, final String url, final String user, final String pass,
			final Map<String, String> additionalHeaders, final Map<String, String> params) {
		return performRequest(contentType, url, user, pass, additionalHeaders, params, HttpModule.POST_TYPE);
	}

	//
	// private methods
	//
	private String performRequest(final String contentType, final String url, final String user, final String pass,
			final Map<String, String> headers, final Map<String, String> params, final int requestType) {

		// add user and pass to client credentials if present
		if ((user != null) && (pass != null)) {
			HttpModule.client.getCredentialsProvider().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(user, pass));
		}

		// process headers using request interceptor
		final Map<String, String> sendHeaders = new HashMap<String, String>();
		// add encoding header for gzip if not present
		if (!sendHeaders.containsKey(HttpModule.ACCEPT_ENCODING)) {
			sendHeaders.put(HttpModule.ACCEPT_ENCODING, HttpModule.GZIP);
		}
		if ((headers != null) && (headers.size() > 0)) {
			sendHeaders.putAll(headers);
		}
		if (requestType == HttpModule.POST_TYPE) {
			sendHeaders.put(HttpModule.CONTENT_TYPE, contentType);
		}
		if (sendHeaders.size() > 0) {
			HttpModule.client.addRequestInterceptor(new HttpRequestInterceptor() {
				@Override
				public void process(final HttpRequest request, final HttpContext context) throws HttpException, IOException {
					for (String key : sendHeaders.keySet()) {
						if (!request.containsHeader(key)) {
							request.addHeader(key, sendHeaders.get(key));
						}
					}
				}
			});
		}

		// handle POST or GET request respectively
		HttpRequestBase method = null;
		if (requestType == HttpModule.POST_TYPE) {
			method = new HttpPost(url);
			// data - name/value params
			List<NameValuePair> nvps = null;
			if ((params != null) && (params.size() > 0)) {
				nvps = new ArrayList<NameValuePair>();
				for (Map.Entry<String, String> entry : params.entrySet()) {
					nvps.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
				}
			}
			if (nvps != null) {
				try {
					HttpPost methodPost = (HttpPost) method;
					methodPost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
				} catch (UnsupportedEncodingException e) {
					throw new RuntimeException("Error peforming HTTP request: " + e.getMessage(), e);
				}
			}
		} else if (requestType == HttpModule.GET_TYPE) {
			method = new HttpGet(url);
		}

		// execute request
		return execute(method);
	}

	private synchronized String execute(final HttpRequestBase method) {
		String response = null;
		// execute method returns?!? (rather than async) - do it here sync, and
		// wrap async elsewhere
		try {
			response = HttpModule.client.execute(method, responseHandler);
		} catch (ClientProtocolException e) {
			response = HttpModule.HTTP_RESPONSE_ERROR + " - " + e.getClass().getSimpleName() + " " + e.getMessage();
		} catch (IOException e) {
			response = HttpModule.HTTP_RESPONSE_ERROR + " - " + e.getClass().getSimpleName() + " " + e.getMessage();
		}
		return response;
	}

	static class GzipDecompressingEntity extends HttpEntityWrapper {
		public GzipDecompressingEntity(final HttpEntity entity) {
			super(entity);
		}

		@Override
		public InputStream getContent() throws IOException, IllegalStateException {
			// the wrapped entity's getContent() decides about repeatability
			InputStream wrappedin = wrappedEntity.getContent();
			return new GZIPInputStream(wrappedin);
		}

		@Override
		public long getContentLength() {
			// length of ungzipped content is not known
			return -1;
		}
	}
}
