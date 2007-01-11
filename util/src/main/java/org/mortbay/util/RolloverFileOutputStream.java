//========================================================================
//Copyright 2006 Mort Bay Consulting Pty. Ltd.
//------------------------------------------------------------------------
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//http://www.apache.org/licenses/LICENSE-2.0
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.
//========================================================================

package org.mortbay.util; 

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.ListIterator;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

/** 
 * RolloverFileOutputStream
 * @author Greg Wilkins 
 */
public class RolloverFileOutputStream extends FilterOutputStream
{
    private static Timer __rollover;
    
    final static String YYYY_MM_DD="yyyy_mm_dd";

    private RollTask _rollTask;
    private SimpleDateFormat _fileBackupFormat =
        new SimpleDateFormat(System.getProperty("ROLLOVERFILE_BACKUP_FORMAT","HHmmssSSS"));
    private SimpleDateFormat _fileDateFormat = 
        new SimpleDateFormat(System.getProperty("ROLLOVERFILE_DATE_FORMAT","yyyy_MM_dd"));

    private String _filename;
    private File _file;
    private boolean _append;
    private int _retainDays;
    
    /* ------------------------------------------------------------ */
    public RolloverFileOutputStream(String filename)
        throws IOException
    {
        this(filename,true,Integer.getInteger("ROLLOVERFILE_RETAIN_DAYS",31).intValue());
    }
    
    /* ------------------------------------------------------------ */
    public RolloverFileOutputStream(String filename, boolean append)
        throws IOException
    {
        this(filename,append,Integer.getInteger("ROLLOVERFILE_RETAIN_DAYS",31).intValue());
    }

    /* ------------------------------------------------------------ */
    public RolloverFileOutputStream(String filename,
                                    boolean append,
                                    int retainDays)
        throws IOException
    {
        this(filename,append,retainDays,TimeZone.getDefault());
    }
    
    /* ------------------------------------------------------------ */
    public RolloverFileOutputStream(String filename,
                                    boolean append,
                                    int retainDays,
                                    TimeZone zone)
        throws IOException
    {
        super(null);
        
        _fileBackupFormat.setTimeZone(zone);
        _fileDateFormat.setTimeZone(zone);
        
        if (filename!=null)
        {
            filename=filename.trim();
            if (filename.length()==0)
                filename=null;
        }
        if (filename==null)
            throw new IllegalArgumentException("Invalid filename");

        _filename=filename;
        _append=append;
        _retainDays=retainDays;
        setFile();
        
        synchronized(RolloverFileOutputStream.class)
        {
            if (__rollover==null)
                __rollover=new Timer();
            
            _rollTask=new RollTask();

             Calendar now = Calendar.getInstance();
             now.setTimeZone(zone);

             GregorianCalendar midnight =
                 new GregorianCalendar(now.get(Calendar.YEAR),
                         now.get(Calendar.MONTH),
                         now.get(Calendar.DAY_OF_MONTH),
                         23,0);
             midnight.setTimeZone(zone);
             midnight.add(Calendar.HOUR,1);
             __rollover.scheduleAtFixedRate(_rollTask,midnight.getTime(),1000L*60*60*24);
        }
    }

    /* ------------------------------------------------------------ */
    public String getFilename()
    {
        return _filename;
    }
    
    /* ------------------------------------------------------------ */
    public String getDatedFilename()
    {
        if (_file==null)
            return null;
        return _file.toString();
    }
    
    /* ------------------------------------------------------------ */
    public int getRetainDays()
    {
        return _retainDays;
    }

    /* ------------------------------------------------------------ */
    private synchronized void setFile()
        throws IOException
    {
        // Check directory
        File file = new File(_filename);
        _filename=file.getCanonicalPath();
        file=new File(_filename);
        File dir= new File(file.getParent());
        if (!dir.isDirectory() || !dir.canWrite())
            throw new IOException("Cannot write log directory "+dir);
            
        Date now=new Date();
        
        // Is this a rollover file?
        String filename=file.getName();
        int i=filename.toLowerCase().indexOf(YYYY_MM_DD);
        if (i>=0)
        {
            file=new File(dir,
                          filename.substring(0,i)+
                          _fileDateFormat.format(now)+
                          filename.substring(i+YYYY_MM_DD.length()));
        }
            
        if (file.exists()&&!file.canWrite())
            throw new IOException("Cannot write log file "+file);

        // Do we need to change the output stream?
        if (out==null || !file.equals(_file))
        {
            // Yep
            _file=file;
            if (!_append && file.exists())
                file.renameTo(new File(file.toString()+"."+_fileBackupFormat.format(now)));
            OutputStream oldOut=out;
            out=new FileOutputStream(file.toString(),_append);
            if (oldOut!=null)
                oldOut.close();
            //if(log.isDebugEnabled())log.debug("Opened "+_file);
        }
    }

    /* ------------------------------------------------------------ */
    private void removeOldFiles()
    {
        if (_retainDays>0)
        {
            Calendar retainDate = Calendar.getInstance();
            retainDate.add(Calendar.DATE,-_retainDays);
            int borderYear = retainDate.get(java.util.Calendar.YEAR);
            int borderMonth = retainDate.get(java.util.Calendar.MONTH) + 1;
            int borderDay = retainDate.get(java.util.Calendar.DAY_OF_MONTH);

            File file= new File(_filename);
            File dir = new File(file.getParent());
            String fn=file.getName();
            int s=fn.toLowerCase().indexOf(YYYY_MM_DD);
            if (s<0)
                return;
            String prefix=fn.substring(0,s);
            String suffix=fn.substring(s+YYYY_MM_DD.length());

            String[] logList=dir.list();
            for (int i=0;i<logList.length;i++)
            {
                fn = logList[i];
                if(fn.startsWith(prefix)&&fn.indexOf(suffix,prefix.length())>=0)
                {        
                    try
                    {
                        StringTokenizer st = new StringTokenizer (fn.substring(prefix.length(), prefix.length()+YYYY_MM_DD.length()), "_.");
                        int nYear = Integer.parseInt(st.nextToken());
                        int nMonth = Integer.parseInt(st.nextToken());
                        int nDay = Integer.parseInt(st.nextToken());
                        
                        if (nYear<borderYear ||
                            (nYear==borderYear && nMonth<borderMonth) ||
                            (nYear==borderYear &&
                             nMonth==borderMonth &&
                             nDay<=borderDay))
                        {
                            //log.info("Log age "+fn);
                            new File(dir,fn).delete();
                        }
                    }
                    catch(Exception e)
                    {
                        //if (log.isDebugEnabled())
                            e.printStackTrace();
                    }
                }
            }
        }
    }

    /* ------------------------------------------------------------ */
    public void write (byte[] buf)
            throws IOException
     {
            out.write (buf);
     }

    /* ------------------------------------------------------------ */
    public void write (byte[] buf, int off, int len)
            throws IOException
     {
            out.write (buf, off, len);
     }
    
    /* ------------------------------------------------------------ */
    /** 
     */
    public void close()
        throws IOException
    {
        synchronized(RolloverFileOutputStream.class)
        {
            try{super.close();}
            finally
            {
                out=null;
                _file=null;
            }

            _rollTask.cancel(); 
        }
    }
    
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    private class RollTask extends TimerTask
    {
        public void run()
        {
            try
            {
                RolloverFileOutputStream.this.setFile();
                RolloverFileOutputStream.this.removeOldFiles();

            }
            catch(IOException e)
            {
                e.printStackTrace();
            }
        }
    }
}
