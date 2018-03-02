package org.stagemonitor.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.xml.bind.DatatypeConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.util.IOUtils;

import com.fasterxml.jackson.databind.JsonNode;

// TODO create HttpRequest POJO
// method, url, headers, outputStreamHandler, responseHandler
// builder methods logErrors(int... excludedStatusCodes)
public class HttpClient {

	private static final long CONNECT_TIMEOUT_SEC = 5;
	private static final long READ_TIMEOUT_SEC = 15;
	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	private static Proxy proxy = null;
	
	//Fix for 434740 - Use another instance of proxy selector and have new proxy objects 
	private static ProxySelector theProxySelector;

    static {
        try {
            Class<?> c = Class.forName("sun.net.spi.DefaultProxySelector");
            if (c != null && ProxySelector.class.isAssignableFrom(c)) {
                theProxySelector = (ProxySelector) c.newInstance();
            }
        } catch (Exception e) {
            theProxySelector = null;
        }
        detectProxy("https://abc.com"); //find the http(s) proxy for a dummy URL
    }

	public int send(final String method, final String url) {
		return send(method, url, null, null);
	}

	public JsonNode getJson(String url, Map<String, String> headers) {
		headers = new HashMap<String, String>(headers);
		headers.put("Accept", "application/json");
		return send("GET", url, headers, null, new ResponseHandler<JsonNode>() {
			@Override
			public JsonNode handleResponse(InputStream is, Integer statusCode, IOException e) throws IOException {
				return JsonUtils.getMapper().readTree(is);
			}
		});
	}

	public int sendAsJson(final String method, final String url, final Object requestBody) {
		return sendAsJson(method, url, requestBody, new HashMap<String, String>());
	}

	public int sendAsJson(final String method, final String url, final Object requestBody, Map<String, String> headerFields) {
		headerFields = new HashMap<String, String>(headerFields);
		headerFields.put("Content-Type", "application/json");
		return send(method, url, headerFields, new OutputStreamHandler() {
			@Override
			public void withHttpURLConnection(OutputStream os) throws IOException {
				writeRequestBody(requestBody, os);
			}
		});
	}

	public int send(String method, String url, final List<String> requestBodyLines) {

		return send(method, url, null, new OutputStreamHandler() {
			@Override
			public void withHttpURLConnection(OutputStream os) throws IOException {
				for (String line : requestBodyLines) {
					os.write(line.getBytes("UTF-8"));
					os.write('\n');
				}
				os.flush();
			}
		});
	}

	public int send(final String method, final String url, final Map<String, String> headerFields, OutputStreamHandler outputStreamHandler) {
		Integer result = send(method, url, headerFields, outputStreamHandler, new ErrorLoggingResponseHandler(url));
		return result == null ? -1 : result;
	}

	public <T> T send(final String method, final String url, final Map<String, String> headerFields,
					  OutputStreamHandler outputStreamHandler, ResponseHandler<T> responseHandler) {

		HttpURLConnection connection = null;
		InputStream inputStream = null;
		String basicAuth;
		try {
			URL parsedUrl = new URL(url);
			//set the default authenticator to null to avoid pop up for authentication
			Authenticator.setDefault(null);
			//get the default system proxy if one exists
//			connection = (HttpURLConnection) parsedUrl.openConnection(proxyLoaded ? proxy : getProxy(url));
			connection = (HttpURLConnection) parsedUrl.openConnection(proxy);
			if (parsedUrl.getUserInfo() != null) {
//				System.out.println("USER_INFO: " + parsedUrl.getUserInfo());
				basicAuth = "Basic " + DatatypeConverter.printBase64Binary(parsedUrl.getUserInfo().getBytes());
				}
			else {
				basicAuth = "Basic " + DatatypeConverter.printBase64Binary(new String("stagemonitor:ae14f#Y!").getBytes());
			}
			connection.setRequestProperty("Authorization", basicAuth);
			connection.setDoOutput(true);
			connection.setRequestMethod(method);
			connection.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(CONNECT_TIMEOUT_SEC));
			connection.setReadTimeout((int) TimeUnit.SECONDS.toMillis(READ_TIMEOUT_SEC));
			if (headerFields != null) {
				for (Map.Entry<String, String> header : headerFields.entrySet()) {
					connection.setRequestProperty(header.getKey(), header.getValue());
				}
			}

			if (outputStreamHandler != null) {
				outputStreamHandler.withHttpURLConnection(connection.getOutputStream());
			}

			inputStream = connection.getInputStream();

			return responseHandler.handleResponse(inputStream, connection.getResponseCode(), null);
		} catch (IOException e) {
			if (connection != null) {
				inputStream = connection.getErrorStream();
				try {
					return responseHandler.handleResponse(inputStream, getResponseCodeIfPossible(connection), e);
				} catch (IOException e1) {
					logger.warn("Error sending {} request to url {}: {}", method, url, e.getMessage(), e);
					logger.warn("Error handling error response for {} request to url {}: {}", method, url, e1.getMessage(), e1);
					try {
						logger.trace(new String(IOUtils.readToBytes(inputStream), "UTF-8"));
					} catch (IOException e2) {
						logger.trace("Could not read error stream: {}", e2.getMessage(), e2);
					}
				}
			} else {
				logger.warn("Error sending {} request to url {}: {}", method, url, e.getMessage(), e);
			}

			return null;
		} finally {
			IOUtils.closeQuietly(inputStream);
		}
	}
	
	private static void detectProxy(String url) {
		Proxy pxy = null;
		try {
			try {
	            System.setProperty("java.net.useSystemProxies","true");
	            URI uri = new URI(url);
				List<Proxy> prxies = theProxySelector != null ? theProxySelector.select(uri) : ProxySelector.getDefault().select(
	                        uri);
				// as per the java doc of ProxySelector.select() method, the
				// list will contain one element even if there are no proxies.
				// So the list will never be empty. we can directly get the
				// first element
	            pxy = prxies.get(0); //get the first one
				
	        } catch (Exception e) {
	            pxy = Proxy.NO_PROXY;
	        }
		} finally {
			if(pxy.type() == Proxy.Type.DIRECT) {
				proxy = Proxy.NO_PROXY;
			} else {
				proxy = new Proxy(pxy.type(), pxy.address()); 
			}
		}
		
	}


	private Integer getResponseCodeIfPossible(HttpURLConnection connection) {
		try {
			return connection.getResponseCode();
		} catch (IOException e) {
			// don't handle exception twice
			return null;
		}
	}

	private void writeRequestBody(Object requestBody, OutputStream os) throws IOException {
		if (requestBody != null) {
			if (requestBody instanceof InputStream) {
				IOUtils.copy((InputStream) requestBody, os);
			} else if (requestBody instanceof String) {
				os.write(((String)requestBody).getBytes("UTF-8"));
			} else {
				JsonUtils.writeJsonToOutputStream(requestBody, os);
			}
			os.flush();
		}
	}

	public interface OutputStreamHandler {
		void withHttpURLConnection(OutputStream os) throws IOException;
	}

	public interface ResponseHandler<T> {
		T handleResponse(InputStream is, Integer statusCode, IOException e) throws IOException;
	}

	private static class ErrorLoggingResponseHandler implements ResponseHandler<Integer> {

		private final Logger logger = LoggerFactory.getLogger(getClass());

		private final String url;

		public ErrorLoggingResponseHandler(String url) {
			this.url = url;
		}

		@Override
		public Integer handleResponse(InputStream is, Integer statusCode, IOException e) throws IOException {
			if (statusCode == null) {
				return -1;
			}
			if (statusCode >= 400) {
				logger.warn(url + ": " + statusCode + " " + IOUtils.toString(is));
			} else {
				IOUtils.consumeAndClose(is);
			}
			return statusCode;
		}
	}
}
