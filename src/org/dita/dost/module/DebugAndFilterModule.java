/*
 * (c) Copyright IBM Corp. 2004, 2005 All Rights Reserved.
 */
package org.dita.dost.module;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import org.dita.dost.exception.DITAOTException;
import org.dita.dost.log.DITAOTJavaLogger;
import org.dita.dost.pipeline.AbstractPipelineInput;
import org.dita.dost.pipeline.AbstractPipelineOutput;
import org.dita.dost.pipeline.PipelineHashIO;
import org.dita.dost.reader.DitaValReader;
import org.dita.dost.reader.ListReader;
import org.dita.dost.util.Constants;
import org.dita.dost.util.FileUtils;
import org.dita.dost.writer.DitaWriter;


/**
 * DebugAndFilterModule implement the second step in preprocess. It will insert debug
 * information into every dita files and filter out the information that is not 
 * necessary.
 * 
 * @author Zhang, Yuan Peng
 */
public class DebugAndFilterModule extends AbstractPipelineModule {
	private DITAOTJavaLogger javaLogger = new DITAOTJavaLogger();
	private static final String [] propertyUpdateList = {"user.input.file",Constants.HREF_TARGET_LIST,
		Constants.CONREF_LIST,Constants.HREF_DITA_TOPIC_LIST,Constants.FULL_DITA_TOPIC_LIST,
		Constants.FULL_DITAMAP_TOPIC_LIST,Constants.CONREF_TARGET_LIST,Constants.COPYTO_SOURCE_LIST,
		Constants.COPYTO_TARGET_TO_SOURCE_MAP_LIST};
	public static String extName = null;
	
    /**
     * @see org.dita.dost.module.AbstractPipelineModule#execute(org.dita.dost.pipeline.AbstractPipelineInput)
     * 
     */
    public AbstractPipelineOutput execute(AbstractPipelineInput input) throws DITAOTException {
        String baseDir = ((PipelineHashIO) input).getAttribute(Constants.ANT_INVOKER_PARAM_BASEDIR);
        String ditavalFile = ((PipelineHashIO) input).getAttribute(Constants.ANT_INVOKER_PARAM_DITAVAL);
        String tempDir = ((PipelineHashIO) input).getAttribute(Constants.ANT_INVOKER_PARAM_TEMPDIR);
        String ext = ((PipelineHashIO) input).getAttribute(Constants.ANT_INVOKER_PARAM_DITAEXT);
       	extName = ext.startsWith(Constants.DOT) ? ext : (Constants.DOT + ext);

        String inputDir = null;
        String filePathPrefix = null;
        ListReader listReader = new ListReader();
        LinkedList parseList = null;
        Content content;
        DitaWriter fileWriter;
        
        
        if (!new File(tempDir).isAbsolute()) {
        	tempDir = new File(baseDir, tempDir).getAbsolutePath();
        }
        if (ditavalFile != null && !new File(ditavalFile).isAbsolute()) {
			ditavalFile = new File(baseDir, ditavalFile).getAbsolutePath();
		}
        
        listReader.read(new File(tempDir, Constants.FILE_NAME_DITA_LIST).getAbsolutePath());
        parseList = (LinkedList) listReader.getContent()
                .getCollection();
        inputDir = (String) listReader.getContent().getValue();
        
        if (!new File(inputDir).isAbsolute()) {
        	inputDir = new File(baseDir, inputDir).getAbsolutePath();
        }
        
        if (ditavalFile!=null){
            DitaValReader filterReader = new DitaValReader();
            filterReader.read(ditavalFile);
            content = filterReader.getContent();
        }else{
            content = new ContentImpl();
        }

        fileWriter = new DitaWriter();
        content.setValue(tempDir);
        fileWriter.setContent(content);
        
        if(inputDir != null){
            filePathPrefix = inputDir + Constants.STICK;
        }
        
        while (!parseList.isEmpty()) {
        	String filename = (String) parseList.removeLast();
        	
        	if (!new File(inputDir, filename).exists()) {
        		// This is an copy-to target file, ignore it
        		continue;
        	}
        	
            /*
             * Usually the writer's argument for write() is used to pass in the
             * ouput file name. But in this case, the input file name is same as
             * output file name so we can use this argument to pass in the input
             * file name. "|" is used to separate the path information that is
             * not necessary to be kept (baseDir) and the path information that
             * need to be kept in the temp directory.
             */        	
			fileWriter.write(
        			new StringBuffer().append(filePathPrefix)
        				.append(filename).toString());
            
        }
        
        updateList(tempDir);
        // reload the property for processing of copy-to
        listReader.read(new File(tempDir, Constants.FILE_NAME_DITA_LIST).getAbsolutePath());
        performCopytoTask(tempDir, listReader.getCopytoMap());

        return null;
    }

    private void updateList(String tempDir){
    	Properties property = new Properties();
    	FileOutputStream output;
    	try{
    		property.load(new FileInputStream( new File(tempDir, Constants.FILE_NAME_DITA_LIST)));
    		
    		for (int i = 0; i < propertyUpdateList.length; i ++){
    			updateProperty(propertyUpdateList[i], property);
    		}
    		
    		output = new FileOutputStream(new File(tempDir, Constants.FILE_NAME_DITA_LIST));
    		property.store(output, null);
    		output.flush();
    		output.close();
    		
    	} catch (Exception e){
    		javaLogger.logException(e);
    	}
    	
    }
    
    private void updateProperty (String listName, Properties property){
    	StringBuffer result = new StringBuffer(Constants.INT_1024);
    	String propValue = property.getProperty(listName);
    	if (propValue == null || Constants.STRING_EMPTY.equals(propValue.trim())){
    		//if the propValue is null or empty
    		return;
    	}
    	
    	StringTokenizer tokenizer = new StringTokenizer(propValue,Constants.COMMA);
    	String file;
    	int equalIndex;
    	
    	while (tokenizer.hasMoreElements()){
    		file = (String)tokenizer.nextElement();
    		equalIndex = file.indexOf(Constants.EQUAL);
    		if(file.indexOf(Constants.FILE_EXTENSION_DITAMAP) != -1){
    			result.append(Constants.COMMA).append(file);
    		} else if (equalIndex == -1 ){
    			//append one more comma at the beginning of property value
    			result.append(Constants.COMMA).append(FileUtils.replaceExtName(file));
    		} else {
    			//append one more comma at the beginning of property value
    			result.append(Constants.COMMA);
    			result.append(FileUtils.replaceExtName(file.substring(0,equalIndex)));
    			result.append(Constants.EQUAL);
    			result.append(FileUtils.replaceExtName(file.substring(equalIndex+1)));
    		}
    	}
    	
    	property.setProperty(listName, result.substring(Constants.INT_1));
    	
    }
    
    
    /*
     * Execute copy-to task, generate copy-to targets base on sources
     */
	private void performCopytoTask(String tempDir, Map copytoMap) {
        Iterator iter = copytoMap.entrySet().iterator();
        while (iter.hasNext()) {
        	Map.Entry entry = (Map.Entry) iter.next();
        	String copytoTarget = (String) entry.getKey();
        	String copytoSource = (String) entry.getValue();        	
        	File srcFile = new File(tempDir, copytoSource);
        	File targetFile = new File(tempDir, copytoTarget);
        	
        	if (targetFile.exists()) {
        		javaLogger
						.logWarn(new StringBuffer("Copy-to task [copy-to=\"")
								.append(copytoTarget)
								.append("\"] which points to an existed file was ignored.").toString());
        	}else{
        		FileUtils.copyFile(srcFile, targetFile);
        	}
        }
	}

}
