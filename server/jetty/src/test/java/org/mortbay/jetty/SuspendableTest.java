package org.mortbay.jetty;

import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import junit.framework.TestCase;

import org.mortbay.jetty.Connector;
import org.mortbay.jetty.HttpConnection;
import org.mortbay.jetty.LocalConnector;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.HandlerWrapper;

public class SuspendableTest extends TestCase
{
    protected Server _server = new Server();
    protected SuspendHandler _handler = new SuspendHandler();
    protected LocalConnector _connector;

    protected void setUp() throws Exception
    {
        _connector = new LocalConnector();
        _server.setConnectors(new Connector[]{ _connector });
        _server.setHandler(_handler);
        _server.start();

    }

    protected void tearDown() throws Exception
    {
        _server.stop();
    }

    public void testSuspendResume() throws Exception
    {
        String response;
        
        _handler.setRead(0);
        _handler.setSuspendFor(10000);
        
        _handler.setResumeAfter(0);
        _handler.setCompleteAfter(-1);
        check("RESUMED",process(null));
        
        _handler.setResumeAfter(100);
        _handler.setCompleteAfter(-1);
        check("RESUMED",process(null));
        
        _handler.setResumeAfter(-1);
        _handler.setCompleteAfter(0);
        check("COMPLETED",process(null));
        
        _handler.setResumeAfter(-1);
        _handler.setCompleteAfter(100);
        check("COMPLETED",process(null));
        

        _handler.setRead(-1);
        
        _handler.setResumeAfter(0);
        _handler.setCompleteAfter(-1);
        check("RESUMED",process("wibble"));
        
        _handler.setResumeAfter(100);
        _handler.setCompleteAfter(-1);
        check("RESUMED",process("wibble"));
        
        _handler.setResumeAfter(-1);
        _handler.setCompleteAfter(0);
        check("COMPLETED",process("wibble"));
        
        _handler.setResumeAfter(-1);
        _handler.setCompleteAfter(100);
        check("COMPLETED",process("wibble"));
        

        _handler.setRead(6);
        
        _handler.setResumeAfter(0);
        _handler.setCompleteAfter(-1);
        check("RESUMED",process("wibble"));
        
        _handler.setResumeAfter(100);
        _handler.setCompleteAfter(-1);
        check("RESUMED",process("wibble"));
        
        _handler.setResumeAfter(-1);
        _handler.setCompleteAfter(0);
        check("COMPLETED",process("wibble"));
        
        _handler.setResumeAfter(-1);
        _handler.setCompleteAfter(100);
        check("COMPLETED",process("wibble"));
    }

    protected void check(String content,String response)
    {
        assertEquals("HTTP/1.1 200 OK",response.substring(0,15));
        assertTrue(content.contains(content));
    }
    
    public synchronized String process(String content) throws Exception
    {
        String request = "GET / HTTP/1.1\r\n" + "Host: localhost\r\n";
        
        if (content==null)
            request+="\r\n";
        else
            request+="Content-Length: "+content.length()+"\r\n" + "\r\n" + content;

        _connector.reopen();
        String response = _connector.getResponses(request);
        return response;
    }
    
    private static class SuspendHandler extends HandlerWrapper
    {
        private int _read;
        private long _suspendFor=-1;
        private long _resumeAfter=-1;
        private long _completeAfter=-1;
        
        public SuspendHandler()
        {}
        
        

        public int getRead()
        {
            return _read;
        }

        public void setRead(int read)
        {
            _read = read;
        }

        public long getSuspendFor()
        {
            return _suspendFor;
        }

        public void setSuspendFor(long suspendFor)
        {
            _suspendFor = suspendFor;
        }

        public long getResumeAfter()
        {
            return _resumeAfter;
        }

        public void setResumeAfter(long resumeAfter)
        {
            _resumeAfter = resumeAfter;
        }

        public long getCompleteAfter()
        {
            return _completeAfter;
        }

        public void setCompleteAfter(long completeAfter)
        {
            _completeAfter = completeAfter;
        }



        public void handle(String target, final HttpServletRequest request, final HttpServletResponse response, int dispatch) throws IOException, ServletException
        {
            final Request base_request = (request instanceof Request)?((Request)request):HttpConnection.getCurrentConnection().getRequest();
            
            if (request.isInitial())
            {
                response.setStatus(200);
                if (_read>0)
                {
                    byte[] buf=new byte[_read];
                    request.getInputStream().read(buf);
                }
                else if (_read<0)
                {
                    InputStream in = request.getInputStream();
                    int b=in.read();
                    while(b!=-1)
                        b=in.read();
                }

                if (_suspendFor>0)
                {
                    request.suspend(_suspendFor);
                }
                else if (_suspendFor==0)
                {
                    request.suspend();
                }

                
                if (_completeAfter>0)
                {
                    new Thread() {
                        public void run()
                        {
                            try
                            {
                                Thread.sleep(_completeAfter);
                                response.getOutputStream().print("COMPLETED");
                                base_request.setHandled(true);
                                request.complete();
                            }
                            catch(Exception e)
                            {
                                e.printStackTrace();
                            }
                        }
                    }.start();
                }
                else if (_completeAfter==0)
                {
                    response.getOutputStream().print("COMPLETED");
                    base_request.setHandled(true);
                    request.complete();
                }
                
                if (_resumeAfter>0)
                {
                    new Thread() {
                        public void run()
                        {
                            try
                            {
                                Thread.sleep(_resumeAfter);
                                request.resume();
                            }
                            catch(Exception e)
                            {
                                e.printStackTrace();
                            }
                        }
                    }.start();
                }
                else if (_resumeAfter==0)
                {
                    request.resume();
                }
            }
            else if (request.isResumed())
            {
                response.getOutputStream().print("RESUMED");
                base_request.setHandled(true);
            }
            else
            {
                response.getOutputStream().print("TIMEOUT");
                base_request.setHandled(true);
            }
        }
    }
}
