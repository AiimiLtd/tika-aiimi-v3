/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
//CHECKSTYLE:OFF
package org.apache.tika.server.core.resource;

import static org.apache.tika.server.core.resource.TikaResource.fillMetadata;
import static org.apache.tika.server.core.resource.TikaResource.fillParseContext;
import static org.apache.tika.server.core.resource.TikaResource.getConfig;

import java.io.InputStream;
import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.listfilter.MetadataListFilter;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.pipes.HandlerConfig;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.apache.tika.server.core.MetadataList;
import org.apache.tika.server.core.TikaServerParseException;

// AIIMI
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Path("/rmeta")
public class RecursiveMetadataResource {

    protected static final String HANDLER_TYPE_PARAM = "handler";
    protected static final BasicContentHandlerFactory.HANDLER_TYPE DEFAULT_HANDLER_TYPE = BasicContentHandlerFactory.HANDLER_TYPE.XML;
    private static final Logger LOG = LoggerFactory.getLogger(RecursiveMetadataResource.class);

    public static List<Metadata> parseMetadata(InputStream is, Metadata metadata, MultivaluedMap<String, String> httpHeaders, UriInfo info, HandlerConfig handlerConfig)
            throws Exception {

        final ParseContext context = new ParseContext();
        Parser parser = TikaResource.createParser();

        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(parser);
        fillMetadata(parser, metadata, httpHeaders);
        fillParseContext(httpHeaders, metadata, context);
        TikaResource.logRequest(LOG, "/rmeta", metadata);

        BasicContentHandlerFactory.HANDLER_TYPE type = handlerConfig.getType();
        RecursiveParserWrapperHandler handler =
                new RecursiveParserWrapperHandler(new BasicContentHandlerFactory(type, handlerConfig.getWriteLimit(), handlerConfig.isThrowOnWriteLimitReached(), context),
                        handlerConfig.getMaxEmbeddedResources(), TikaResource
                        .getConfig()
                        .getMetadataFilter());
        try {
            TikaResource.parse(wrapper, LOG, "/rmeta", is, handler, metadata, context);
        } catch (TikaServerParseException e) {
            //do nothing
            LOG.debug("server parse exception", e);
        } catch (SecurityException | WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            //we shouldn't get here?
            LOG.error("something went seriously wrong", e);
        }
        MetadataListFilter metadataListFilter = context.get(MetadataListFilter.class, getConfig().getMetadataListFilter());
        return metadataListFilter.filter(handler.getMetadataList());
    }

    static HandlerConfig buildHandlerConfig(MultivaluedMap<String, String> httpHeaders, String handlerTypeName, HandlerConfig.PARSE_MODE parseMode) {
        int writeLimit = -1;
        if (httpHeaders.containsKey("writeLimit")) {
            writeLimit = Integer.parseInt(httpHeaders.getFirst("writeLimit"));
        }

        int maxEmbeddedResources = -1;
        if (httpHeaders.containsKey("maxEmbeddedResources")) {
            maxEmbeddedResources = Integer.parseInt(httpHeaders.getFirst("maxEmbeddedResources"));
        }
        return new HandlerConfig(BasicContentHandlerFactory.parseHandlerType(handlerTypeName, DEFAULT_HANDLER_TYPE), parseMode, writeLimit, maxEmbeddedResources,
                TikaResource.getThrowOnWriteLimitReached(httpHeaders));
    }

    /**
     * Returns an InputStream that can be deserialized as a list of
     * {@link Metadata} objects.
     * The first in the list represents the main document, and the
     * rest represent metadata for the embedded objects.  This works
     * recursively through all descendants of the main document, not
     * just the immediate children.
     * <p>
     * The extracted text content is stored with the key
     * {@link org.apache.tika.metadata.TikaCoreProperties#TIKA_CONTENT}.
     * <p>
     * Specify the handler for the content (xml, html, text, ignore)
     * in the path:<br/>
     * /rmeta/form (default: xml)<br/>
     * /rmeta/form/xml    (store the content as xml)<br/>
     * /rmeta/form/text   (store the content as text)<br/>
     * /rmeta/form/ignore (don't record any content)<br/>
     *
     * @param att             attachment
     * @param info            uri info
     * @param handlerTypeName which type of handler to use
     * @return InputStream that can be deserialized as a list of {@link Metadata} objects
     * @throws Exception
     */
    @POST
    @Consumes("multipart/form-data")
    @Produces({"application/json"})
    //@Path("form{" + HANDLER_TYPE_PARAM + " : (\\w+)?}")
    @Path("form{" + HANDLER_TYPE_PARAM + " : (\\w+)?}/{timeout}")
    public Response getMetadataFromMultipart(
        Attachment att,
        @Context UriInfo info,
        @PathParam(HANDLER_TYPE_PARAM) String handlerTypeName,
        @PathParam("timeout") int timeout) throws Exception {

        /*return Response
                .ok(parseMetadataToMetadataList(att.getObject(InputStream.class), new Metadata(), att.getHeaders(), info,
                        buildHandlerConfig(att.getHeaders(), handlerTypeName, HandlerConfig.PARSE_MODE.RMETA)))
                .build(); */

        // AIIMI
		return runTask(att.getObject(InputStream.class), new Metadata(), att.getHeaders(), info, handlerTypeName, timeout);
    }

    /**
     * Returns an InputStream that can be deserialized as a list of
     * {@link Metadata} objects.
     * The first in the list represents the main document, and the
     * rest represent metadata for the embedded objects.  This works
     * recursively through all descendants of the main document, not
     * just the immediate children.
     * <p>
     * The extracted text content is stored with the key
     * {@link org.apache.tika.metadata.TikaCoreProperties#TIKA_CONTENT}.
     * <p>
     * Specify the handler for the content (xml, html, text, ignore)
     * in the path:<br/>
     * /rmeta (default: xml)<br/>
     * /rmeta/xml    (store the content as xml)<br/>
     * /rmeta/text   (store the content as text)<br/>
     * /rmeta/ignore (don't record any content)<br/>
     *
     * @param info            uri info
     * @param handlerTypeName which type of handler to use
     * @return InputStream that can be deserialized as a list of {@link Metadata} objects
     * @throws Exception
     */

    @PUT
    @Produces("application/json")
    //@Path("{" + HANDLER_TYPE_PARAM + " : (\\w+)?}")
    @Path("{" + HANDLER_TYPE_PARAM + "}/{timeout}")
    public Response getMetadata(
        InputStream is,
        @Context HttpHeaders httpHeaders,
        @Context UriInfo info,
        @PathParam(HANDLER_TYPE_PARAM) String handlerTypeName,
        @PathParam("timeout") int timeout) throws Exception {
        
        /*Metadata metadata = new Metadata();
        return Response
                .ok(parseMetadataToMetadataList(TikaResource.getInputStream(is, metadata, httpHeaders, info), metadata, httpHeaders.getRequestHeaders(), info,
                        buildHandlerConfig(httpHeaders.getRequestHeaders(), handlerTypeName, HandlerConfig.PARSE_MODE.RMETA)))
                .build(); */

        // AIIMI
		Metadata metadata = new Metadata();
		return runTask(TikaResource.getInputStream(is, metadata, httpHeaders, info), metadata, httpHeaders.getRequestHeaders(), info, handlerTypeName, timeout);
    }

    private MetadataList parseMetadataToMetadataList(InputStream is, Metadata metadata, MultivaluedMap<String, String> httpHeaders, UriInfo info, HandlerConfig handlerConfig)
            throws Exception {
        return new MetadataList(parseMetadata(is, metadata, httpHeaders, info, handlerConfig));
    }

	/* AIIMI to add timeout */
	//protected Response runTask(InputStream inputStream, MultivaluedMap<String, String> httpHeaders,UriInfo info,String handlerTypeName, int timeout)
	protected Response runTask(InputStream is,
								Metadata metadata,
								MultivaluedMap<String, String> httpHeaders,
								UriInfo info,
								String handlerTypeName,
								int timeout)
		throws InterruptedException, ExecutionException{
		
		RMetaParseTask rMetaParseTask = new RMetaParseTask();
    	rMetaParseTask.setInputStream(is);
		rMetaParseTask.setMetadata(metadata);
    	rMetaParseTask.setHttpHeaders(httpHeaders);
    	rMetaParseTask.setInfo(info);
    	rMetaParseTask.setHandlerTypeName(handlerTypeName);
    	
		ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<MetadataList> future = executor.submit(rMetaParseTask);
         
        try{
            return Response.ok(future.get(timeout, TimeUnit.SECONDS)).build();
        }
        catch(TimeoutException e){            
            LOG.warn("Cancelled rmeta call as it took longer than [" + timeout + "] seconds");
            Response response = Response.status(504, "Cancelled rmeta call as it took longer than [" + timeout + "] seconds").build();
            future.cancel(true);
            return response;
        }
        finally{
        	 executor.shutdownNow();
        }
	}
	
	public class RMetaParseTask implements Callable<MetadataList> {

		protected InputStream _is = null;
		protected Metadata _metadata = null;
		protected MultivaluedMap<String, String> _httpHeaders = null; 
		protected UriInfo _info = null;
		protected String _handlerTypeName = null;
		
		public void setInputStream(InputStream is){
			_is = is;
		}
		
		public void setMetadata(Metadata metadata){
			_metadata = metadata;
		}
		
		public void setHttpHeaders(MultivaluedMap<String, String> httpHeaders){
			_httpHeaders = httpHeaders;
		}
		
		public void setInfo(UriInfo info){
			_info = info;
		}
		
		public void setHandlerTypeName(String handlerTypeName){
			_handlerTypeName = handlerTypeName;
		}
		
	    @Override
	    public MetadataList call()
	    	throws Exception {	    
	    	//return parseMetadata(_is,_httpHeaders, _info, _handlerTypeName);
			return parseMetadataToMetadataList(_is,
											   _metadata,
											   _httpHeaders,
											   _info,
											   buildHandlerConfig(_httpHeaders, _handlerTypeName, HandlerConfig.PARSE_MODE.RMETA)
											   );
	    }
	}
}
//CHECKSTYLE:ON