package proxy;

import java.net.*;
import java.io.*;
import java.util.*;

public class MyProxy extends PrivacyProxy {

    //////////////////////////////////////////////////////////////////////////
    //
    // Enhance your proxy by implementing the following three methods:
    //   - onRequest
    //   - onResponse
    //
    //////////////////////////////////////////////////////////////////////////

    protected HashMap<String, String> onRequest(HashMap<String, String> requestHeaders, String url){
    // This is the onRequest handler.
    // It will be executed whenever an HTTP request passes by.
    // Its arguments are a so-called HashMap containg all headers from the request, and a simple String containing the requested URL.
    // You can put code here to print the request headers, and to modify them.

        // let's simply print the requested URL, for a start that's enough:
        log("Request for: " + url);

        // if we want to print all the request headers , use the below code:
        // it does a for-loop over all headers

       if (url.contains("analytics")) {
    	   return null;
       }
       
       if (url.contains("pagead")) {
    	   return null;
       }

       if (url.contains("plugin")) {
    	   return null;
       }
       
        // example code to do something if a certain requestheader is present:
      
        if (requestHeaders.containsKey("User-Agent")) {
        	requestHeaders.replace("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_3) AppleWebKit/537.75.14 (KHTML, like Gecko) Version/7.0.3 Safari/7046A194A");
        }
       
        if (requestHeaders.containsKey("Cache-Control")) {
        	requestHeaders.replace("Cache-Control", "no-store");
        }

        // example code to remove the  Creepyness  header:
        if (requestHeaders.containsKey("Cookie")) {
        requestHeaders.remove("Cookie");
        }
        if (requestHeaders.containsKey("Referer")) {
        	if (!url.contains(requestHeaders.get("Referer"))) {
        		return null;
        	}
        }
        
        requestHeaders.replace("Accept-Encoding", "identity");

        // example code to insert (or replace) the  Niceness  header:

        requestHeaders.put("Accept-Language","en-US;q=1.0");
        requestHeaders.put("DNT", "1");

        for (String header : requestHeaders.keySet()) {
            // within the for loop, the variable  header  contains the name of the header
            // and you can ask for the contents of that header using requestHeaders.get() .
            log("  REQ: " + header + ": " + requestHeaders.get(header));
        }
        // return the (manipulated) headers, or
        return requestHeaders;

        // alternatively, drop this request by returning null
        // return null;
    }


    protected byte[] onResponse(byte[] originalBytes, String httpresponse){
    // This is the onResponse handler.
    // It will be executed whenever an HTTP reply passes by.
    // Its arguments are the entire HTTP response (both headers and data) as a byte array, and the line containing the response code.
    // For your convenience, the response headers are also available as a HashMap called responseHeaders , but you can't modify it.
        log("Response: "+httpresponse);

        // if you want to (safely, i.e., without binary garbage) print the entire response, uncomment the following:
        
        //printSafe(originalBytes);


        // if you want to modify the response, you can either modify the byte array directly,
        // or first convert it to a string and then modify that, _if_ you know for sure the response is in text form
        // (otherwise, a string doesn't make sense).

        if (responseHeaders.containsKey("Content-Type") && responseHeaders.get("Content-Type").startsWith("text/html")) {
             String s = new String(originalBytes);
             String s2 = s.replaceAll("navigator", "");
             String s3 = s2.replaceAll("<script type=\"text/javascript\" src=\"http://apis.google.com/js/plusone.js\"></script>", "");
             String s4 = s3.replaceAll("<iframe src=\"http://www\\.facebook\\.com/plugins/like\\.php.*?</iframe>", "");
             byte [] alteredBytes = s4.getBytes();
             return alteredBytes;
        }

        // return the original, unmodified array:
        return originalBytes;
    }

    
    // Constructor, no need to touch this
    public MyProxy(Socket socket, Boolean autoFlush) {
        super(socket, autoFlush);
    }
}
