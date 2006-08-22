/**
 * 
 */
package org.mortbay.jetty.nio;

import org.mortbay.io.Buffer;
import org.mortbay.io.nio.NIOBuffer;
import org.mortbay.jetty.AbstractConnector;

/* ------------------------------------------------------------ */
/**
 * @author gregw
 *
 */
public abstract class AbstractNIOConnector extends AbstractConnector implements NIOConnector
{

    private boolean _useDirectBuffers=true;
    


    /* ------------------------------------------------------------------------------- */
    public boolean getUseDirectBuffers()
    {
        return _useDirectBuffers;
    }

    /* ------------------------------------------------------------------------------- */
    /**
     * @param direct If True (the default), the connector can use NIO direct buffers.
     * Some JVMs have memory management issues (bugs) with direct buffers.
     */
    public void setUseDirectBuffers(boolean direct)
    {
        _useDirectBuffers=direct;
    }


    /* ------------------------------------------------------------------------------- */
    protected Buffer newBuffer(int size)
    {
        // TODO
        // Header buffers always byte array buffers (efficiency of random access)
        // There are lots of things to consider here... DIRECT buffers are faster to
        // send but more expensive to build and access! so we have choices to make...
        // + headers are constructed bit by bit and parsed bit by bit, so INDiRECT looks
        // good for them.   
        // + but will a gather write of an INDIRECT header with a DIRECT body be any good?
        // this needs to be benchmarked.
        // + Will it be possible to get a DIRECT header buffer just for the gather writes of
        // content from file mapped buffers?  
        // + Are gather writes worth the effort?  Maybe they will work well with two INDIRECT
        // buffers being copied into a single kernel buffer?
        // 
        if (size==getHeaderBufferSize())
            return new NIOBuffer(size, NIOBuffer.INDIRECT);
        return new NIOBuffer(size, _useDirectBuffers?NIOBuffer.DIRECT:NIOBuffer.INDIRECT);
    }

}
