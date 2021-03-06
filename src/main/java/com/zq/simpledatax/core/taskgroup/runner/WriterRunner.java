package com.zq.simpledatax.core.taskgroup.runner;

import java.util.concurrent.Callable;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zq.simpledatax.common.plugin.AbstractTaskPlugin;
import com.zq.simpledatax.common.plugin.RecordReceiver;
import com.zq.simpledatax.common.spi.Writer;
import com.zq.simpledatax.common.statistics.PerfRecord;
import com.zq.simpledatax.core.statistics.communication.CommunicationTool;
import com.zq.simpledatax.helper.TimeLogHelper;

/**
 * Created by jingxing on 14-9-1.
 * <p/>
 * 单个slice的writer执行调用
 */
public class WriterRunner extends AbstractRunner implements Callable<Boolean> {

    private static final Logger LOG = LoggerFactory
            .getLogger(WriterRunner.class);

    private RecordReceiver recordReceiver;

    public void setRecordReceiver(RecordReceiver receiver) {
        this.recordReceiver = receiver;
    }

    public WriterRunner(AbstractTaskPlugin abstractTaskPlugin) {
        super(abstractTaskPlugin);
    }

    @Override
    public Boolean call() throws Exception{
        Validate.isTrue(this.recordReceiver != null);
        TimeLogHelper.start("taskWriter");
        
        Writer.Task taskWriter = (Writer.Task) this.getPlugin();
        //统计waitReadTime，并且在finally end
        PerfRecord channelWaitRead = new PerfRecord(getTaskGroupId(), getTaskId(), PerfRecord.PHASE.WAIT_READ_TIME);
        try {
            channelWaitRead.start();
            LOG.debug("task writer starts to do init ...");
            PerfRecord initPerfRecord = new PerfRecord(getTaskGroupId(), getTaskId(), PerfRecord.PHASE.WRITE_TASK_INIT);
            initPerfRecord.start();
            taskWriter.init();
            initPerfRecord.end();

            LOG.debug("task writer starts to do prepare ...");
            PerfRecord preparePerfRecord = new PerfRecord(getTaskGroupId(), getTaskId(), PerfRecord.PHASE.WRITE_TASK_PREPARE);
            preparePerfRecord.start();
            taskWriter.prepare();
            preparePerfRecord.end();
            LOG.debug("task writer starts to write ...");

            PerfRecord dataPerfRecord = new PerfRecord(getTaskGroupId(), getTaskId(), PerfRecord.PHASE.WRITE_TASK_DATA);
            dataPerfRecord.start();
            taskWriter.startWrite(recordReceiver);

            dataPerfRecord.addCount(CommunicationTool.getTotalReadRecords(super.getRunnerCommunication()));
            dataPerfRecord.addSize(CommunicationTool.getTotalReadBytes(super.getRunnerCommunication()));
            dataPerfRecord.end();

            LOG.debug("task writer starts to do post ...");
            PerfRecord postPerfRecord = new PerfRecord(getTaskGroupId(), getTaskId(), PerfRecord.PHASE.WRITE_TASK_POST);
            postPerfRecord.start();
            taskWriter.post();
            postPerfRecord.end();
            super.markSuccess();        
        } catch (Throwable e) {
            LOG.error("Writer Runner Received Exceptions:{}",e);
            super.markFail(e);
            return false;
        } finally {
            LOG.debug("task writer starts to do destroy ...");
            try {
            	PerfRecord desPerfRecord = new PerfRecord(getTaskGroupId(), getTaskId(), PerfRecord.PHASE.WRITE_TASK_DESTROY);
            	desPerfRecord.start();
            	super.destroy();
            	desPerfRecord.end();
            	channelWaitRead.end(super.getRunnerCommunication().getLongCounter(CommunicationTool.WAIT_READER_TIME));            	
            }catch (Exception e) {
            	e.printStackTrace();
            }
            TimeLogHelper.end("taskWriter");
        }
		return true;
    }
    
    public boolean supportFailOver(){
    	Writer.Task taskWriter = (Writer.Task) this.getPlugin();
    	return taskWriter.supportFailOver();
    }

    public void shutdown(){
        recordReceiver.shutdown();
    }
}
