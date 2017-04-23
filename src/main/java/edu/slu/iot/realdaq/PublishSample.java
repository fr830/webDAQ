package edu.slu.iot.realdaq;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder;

import com.amazonaws.services.iot.client.AWSIotException;
import com.amazonaws.services.iot.client.AWSIotQos;
import com.amazonaws.services.iot.client.AWSIotTimeoutException;

import edu.slu.iot.IoTClient;
import edu.slu.iot.data.DaqState;
import edu.slu.iot.data.State;
import edu.slu.iot.data.StateListener;
import edu.slu.iot.data.StateSink;

public class PublishSample {
	
	private static final String adcReader = "src/main/c/ECE_Capstone/reader";
	private static Process adcReaderProcess = null;
	
	public static void main(String args[]) throws InterruptedException, AWSIotException, AWSIotTimeoutException {
   
		IoTClient client = new IoTClient("Certificate1/conf.txt");
		
    	
		new StateSink<DaqState>(client, DaqState.class, new StateListener() {
			  
			@Override
			public <T extends State> void onStateChangeSucceded(T state) {
				if (state instanceof DaqState) {
					
					DaqState daqState = (DaqState) state;
					
					// destroy an existing process if it exists
					if (adcReaderProcess != null) {
						adcReaderProcess.destroy();
					}
					
					// CONFIGURE DIGITAL POTS HERE
					System.out.println("state changed to: " + daqState.serialize());
					
					// create new process
					try {
						// adcReaderProcess = new ProcessBuilder(adcReader).start();
						adcReaderProcess = new ProcessBuilder(adcReader, daqState.getFrequency().toString()).start();
					} catch (IOException e) {
						e.printStackTrace();
					}					
					
					// start publishing
					try {
						client.publish(new DaqPublisher(client, daqState.getTopic(), AWSIotQos.QOS0, adcReaderProcess));
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					
					try {
						adcReaderProcess.waitFor();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		  
	  });  

    }
}
