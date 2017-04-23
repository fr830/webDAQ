package edu.slu.iot.client;

import java.awt.EventQueue;

import javax.swing.JFrame;
import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.NumberFormatter;
import javax.swing.text.PlainDocument;
import javax.swing.JButton;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

import edu.slu.iot.IoTClient;
import edu.slu.iot.client.Strand;
import edu.slu.iot.data.DaqState;
import edu.slu.iot.data.Sample;
import edu.slu.iot.data.StateSource;

import javax.swing.JFileChooser;
import javax.swing.JTextField;
import net.miginfocom.swing.MigLayout;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import com.amazonaws.services.iot.client.AWSIotException;
import com.amazonaws.services.iot.client.AWSIotQos;
import com.amazonaws.services.iot.client.AWSIotTimeoutException;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.QueryOutcome;
import com.amazonaws.services.dynamodbv2.document.RangeKeyCondition;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;

import javax.swing.JScrollPane;
import java.awt.SystemColor;
import javax.swing.JList;
import javax.swing.AbstractListModel;
import javax.swing.JSeparator;
import javax.swing.JCheckBox;
import com.github.lgooddatepicker.components.*;
import com.github.lgooddatepicker.components.DateTimePicker;
import com.github.lgooddatepicker.components.TimePickerSettings.TimeIncrement;
import javax.swing.JFormattedTextField;

public class StrandWindow {

	private StrandListener sListener;
	private DaqState stateSyncObject;
	private File configFile = null;
	private File writeFile = null;
	private IoTClient iotClient;
	private JList listView;
	private AppendableView listModel = new AppendableView();
	private boolean iotConnected = false;
	
	private JFrame frame;
	private JTextField topicField;
	private String topicString;
	private JFormattedTextField gainField;
	private JFormattedTextField frequencyField;
	private JButton connectButton;
	private JButton updateStateButton;
	private JButton allPastDataButton;
	private JButton rangePastDataButton;
	private JCheckBox scrollingCheckBox;
	private JTextPane connectionStatus;
	private JTextPane topicStatus;
	private JTextPane gainStatus;
	private JTextPane frequencyStatus;
	
	static private AmazonDynamoDB dynamoDB;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}
		catch (Exception e){
			System.out.println("UIManager Error");
			e.printStackTrace();
		}
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					StrandWindow window = new StrandWindow();
					window.frame.setVisible(true);
					window.connectionListener();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 */
	public StrandWindow() {
		initialize(); 
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		frame = new JFrame();
		frame.setBounds(100, 100, 600, 425);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().setLayout(new MigLayout("", "[132.00px,grow][48.00px][114.00:104.00][74.00,grow]", "[25.00px][][8.00px,grow][][grow][grow][grow][][19.00][][grow][][1.00][27.00,grow][grow]"));
		
		JTextPane txtpnChooseAConfiguration = new JTextPane();
		txtpnChooseAConfiguration.setBackground(SystemColor.control);
		txtpnChooseAConfiguration.setText("Choose a configuration file (.conf)");
		frame.getContentPane().add(txtpnChooseAConfiguration, "cell 0 0,alignx left,aligny center");
		
		connectButton = new JButton("Connect");
		connectButton.setEnabled(false);
		frame.getContentPane().add(connectButton, "cell 1 2,growx,aligny center");
		
		
		connectionStatus = new JTextPane();
		connectionStatus.setBackground(SystemColor.control);
		connectionStatus.setText("Status: Not Connected");
		frame.getContentPane().add(connectionStatus, "cell 2 2,alignx center,aligny center");
		
		JSeparator secondSeparator = new JSeparator();
		frame.getContentPane().add(secondSeparator, "cell 0 3 4 1,growx");
		
		JTextPane txtpnChangeDeviceState = new JTextPane();
		txtpnChangeDeviceState.setBackground(SystemColor.menu);
		txtpnChangeDeviceState.setText("Change device state");
		frame.getContentPane().add(txtpnChangeDeviceState, "cell 0 4,grow");
		
		JTextPane txtpnTopic = new JTextPane();
		txtpnTopic.setBackground(SystemColor.menu);
		txtpnTopic.setText("Topic:");
		frame.getContentPane().add(txtpnTopic, "cell 1 4,grow");
	
		topicField = new JTextField();
		topicField.setDocument(new JTextFieldLimiter(100)); //limit length to 100 characters
		frame.getContentPane().add(topicField, "cell 2 4,alignx left,aligny center");
		topicField.setColumns(10);
		
		topicStatus = new JTextPane();
		topicStatus.setText("Current topic: ");
		topicStatus.setBackground(SystemColor.menu);
		frame.getContentPane().add(topicStatus, "cell 3 4,grow");
		
		updateStateButton = new JButton("Update");
		updateListener();
	    updateStateButton.setEnabled(false);
		frame.getContentPane().add(updateStateButton, "cell 0 5,growx,aligny center");
		
		JTextPane txtpnGain = new JTextPane();
		txtpnGain.setBackground(SystemColor.menu);
		txtpnGain.setText("Gain:");
		frame.getContentPane().add(txtpnGain, "cell 1 5,grow");

		NumberFormatter gainFormat = new NumberFormatter(new DecimalFormat("#0.0000"));
		gainField = new JFormattedTextField(gainFormat);
		gainField.setColumns(10);
		frame.getContentPane().add(gainField, "cell 2 5,alignx left,aligny center");
		
		gainStatus = new JTextPane();
		gainStatus.setText("Current gain: 0.75");
		gainStatus.setBackground(SystemColor.menu);
		frame.getContentPane().add(gainStatus, "cell 3 5,grow");
		
		JTextPane txtpnFrequency = new JTextPane();
		txtpnFrequency.setBackground(SystemColor.menu);
		txtpnFrequency.setText("Frequency:");
		frame.getContentPane().add(txtpnFrequency, "cell 1 6,grow");

		NumberFormatter frequencyFormat = new NumberFormatter(new DecimalFormat("#00000.00"));
		frequencyField = new JFormattedTextField(frequencyFormat);
		frequencyField.setColumns(10);
		frame.getContentPane().add(frequencyField, "cell 2 6,alignx left,aligny center");
		
		frequencyStatus = new JTextPane();
		frequencyStatus.setText("Current frequency: 25 kHz");
		frequencyStatus.setBackground(SystemColor.menu);
		frame.getContentPane().add(frequencyStatus, "cell 3 6,grow");
		
		
		JSeparator thirdSeparator = new JSeparator();
		frame.getContentPane().add(thirdSeparator, "cell 0 8 4 1,grow");
		
		JTextPane pastDataTextBox = new JTextPane();
		pastDataTextBox.setBackground(SystemColor.menu);
		pastDataTextBox.setText("Start time:");
		frame.getContentPane().add(pastDataTextBox, "cell 0 9,alignx left,aligny center");
		
		JButton btnBrowse = new JButton("Browse...");
		btnBrowse.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent arg0) {
				JFileChooser configFileChooser = new JFileChooser();
				File workingDirectory = new File(System.getProperty("user.dir"));
				configFileChooser.setCurrentDirectory(workingDirectory);
				frame.getContentPane().add(configFileChooser);
				int chooseStatus = configFileChooser.showOpenDialog(frame);
				if (chooseStatus == JFileChooser.APPROVE_OPTION) {
                    configFile = configFileChooser.getSelectedFile();
                    connectButton.setEnabled(true);
                    allPastDataButton.setEnabled(true);
				}
			}
		});
		frame.getContentPane().add(btnBrowse, "cell 1 0,growx,aligny center");
		
		JSeparator firstSeparator = new JSeparator();
		frame.getContentPane().add(firstSeparator, "cell 0 1 4 1,grow");
		
		JTextPane txtpnEnterTheTopic = new JTextPane();
		txtpnEnterTheTopic.setBackground(SystemColor.control);
		txtpnEnterTheTopic.setText("Connect to a device");
		frame.getContentPane().add(txtpnEnterTheTopic, "cell 0 2,alignx left,aligny center");
		
		TimePickerSettings timeSettings1 = new TimePickerSettings();
		timeSettings1.setInitialTimeToNow();
		timeSettings1.generatePotentialMenuTimes(TimeIncrement.FiveMinutes, null, null);
		DatePickerSettings dateSettings1 = new DatePickerSettings();
		DateTimePicker startDateTimePicker = new DateTimePicker(dateSettings1, timeSettings1);
		frame.getContentPane().add(startDateTimePicker, "cell 1 9 2 1,alignx left,growy");
		
		allPastDataButton = new JButton("Add all past data");
		frame.getContentPane().add(allPastDataButton, "cell 3 9,growx,aligny center");
		allPastDataButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {				
				loadHistoricalData();
			}
		});
		allPastDataButton.setEnabled(false);
		
		JTextPane txtpnEndTime = new JTextPane();
		txtpnEndTime.setBackground(SystemColor.menu);
		txtpnEndTime.setText("End time:");
		frame.getContentPane().add(txtpnEndTime, "cell 0 10,grow");
		
		TimePickerSettings timeSettings2 = new TimePickerSettings();
		timeSettings2.setInitialTimeToNow();
		timeSettings2.generatePotentialMenuTimes(TimeIncrement.FiveMinutes, null, null);
		DatePickerSettings dateSettings2 = new DatePickerSettings();
		DateTimePicker stopDateTimePicker = new DateTimePicker(dateSettings2, timeSettings2);
		frame.getContentPane().add(stopDateTimePicker, "cell 1 10 2 1,alignx left,growy");
		
		rangePastDataButton = new JButton("Add range of past data");
		rangePastDataButton.setEnabled(false);
		rangePastDataButton.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent arg0) {
					ZoneId zoneId = ZoneId.systemDefault();
					long start = startDateTimePicker.getDateTimePermissive().atZone(zoneId).toEpochSecond();
					long end = startDateTimePicker.getDateTimePermissive().atZone(zoneId).toEpochSecond();
					if ((startDateTimePicker.getDatePicker().getDate() != null) && (stopDateTimePicker.getDatePicker().getDate() != null))
						loadHistoricalData(start, end);
				}
		});
		frame.getContentPane().add(rangePastDataButton, "cell 3 10,growx,aligny center");
		
		JSeparator fourthSeparator = new JSeparator();
		frame.getContentPane().add(fourthSeparator, "cell 0 11 4 1,grow");
		
		JTextPane txtpnChooseAFile = new JTextPane();
		txtpnChooseAFile.setText("Choose or create a file to write to");
		txtpnChooseAFile.setBackground(SystemColor.menu);
		frame.getContentPane().add(txtpnChooseAFile, "cell 0 13,growx,aligny center");
		JButton writeButton = new JButton("Write to file");
		writeButton.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent arg0) {
				if (writeFile != null) {
					File target = new File(writeFile.getPath());
					if (!target.exists())
						try {
							target.createNewFile();
						} catch (IOException e) {
							System.out.println("Could not create new file " + writeFile.getPath());
							e.printStackTrace();
						}
					FileOutputStream fstream = null;
					try {fstream = new FileOutputStream(target);}
					catch (FileNotFoundException fnfe) {
						System.out.println("File not found exception in file write");
					}
					BufferedWriter bwriter = new BufferedWriter(new OutputStreamWriter(fstream));
					try {
						Sample sample = null;
						for (int i = 0; i < listModel.getSize(); i++) {
							sample = listModel.getElementAt(i);
							long timeStamp = sample.getTimestamp();
							long seconds = timeStamp >> 32;
							long nanoseconds = timeStamp & 0x0000FFFF;
							bwriter.write(Long.toString(seconds) + "." + nanoseconds + ", " + Float.toString(sample.getValue()));
							bwriter.newLine();
						}
						bwriter.close();
					}
					catch (IOException ioe) {
						System.out.println("IOException on file write");
					}
				}
			}
		});
		writeButton.setEnabled(false);
		frame.getContentPane().add(writeButton, "cell 2 13,growx,aligny center");
		
		JButton writePathButton = new JButton("Find file...");
		writePathButton.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent arg0) {
				JFileChooser writingFileChooser = new JFileChooser();
				File workingDirectory = new File(System.getProperty("user.dir"));
				writingFileChooser.setCurrentDirectory(workingDirectory);
				frame.getContentPane().add(writingFileChooser);
				int chooseStatus = writingFileChooser.showOpenDialog(frame);
				if (chooseStatus == JFileChooser.APPROVE_OPTION) {
                    writeFile = writingFileChooser.getSelectedFile();
                    writeButton.setEnabled(true);
				}
			}
		});
		frame.getContentPane().add(writePathButton, "cell 1 13,growx,aligny center");
		
		scrollingCheckBox = new JCheckBox("Scroll to Bottom");
		frame.getContentPane().add(scrollingCheckBox, "cell 3 13,growx,aligny center");
		scrollingCheckBox.setSelected(true);
		
		JScrollPane scrollPane = new JScrollPane();
		frame.getContentPane().add(scrollPane, "cell 0 14 4 1,grow");
		
		listView = new JList(listModel);
		scrollPane.setViewportView(listView);
		
	}
	
	public void loadHistoricalData() {
		loadHistoricalData(Long.MIN_VALUE, Long.MAX_VALUE);
	}
	
	public void loadHistoricalData(long startTime, long endTime) {
		QuerySpec spec;
		if (topicString == null) {
			System.out.println("Please enter your session ID.");
		}
		else {
			if (listModel.getSize() != 0) { //if we have any data from this session so far, only query data from before it began
				endTime = listModel.getElementAt(0).getTimestamp();
			}
			spec = new QuerySpec()
				.withRangeKeyCondition(new RangeKeyCondition("timestamp").between(startTime, endTime))
				.withHashKey("sessionID", topicString);
			dynamoDB = AmazonDynamoDBClientBuilder.standard()
					.withRegion(Regions.US_WEST_2)
					.withCredentials(new ProfileCredentialsProvider("Certificate1/ddbconf.txt", "default"))
					.build();
			Table table = new Table(dynamoDB, getTableName());
			ItemCollection<QueryOutcome> items = table.query(spec);
			Iterator<Item> iterator = items.iterator();
			Item item = null;
			List<Sample> writeToView = new ArrayList<Sample>();
			while (iterator.hasNext()) { //make sure this doesn't interrupt rendering too much
			    item = iterator.next();
			    writeToView.add(new Sample(item));
			}
			listModel.addBulkToList(writeToView);
		}
	}
	
	public void connectionListener() {
		connectButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
					if (iotConnected) {
						try {
							iotClient.disconnect();
						} catch (AWSIotException e) {
							e.printStackTrace();
						}
						iotConnected = false;
				        connectButton.setText("Connect");
				        connectionStatus.setText("Status: Not Connected");
					    updateStateButton.setEnabled(false);
					}
					else {
						try {
							iotClient = new IoTClient(configFile.getPath());
					        listModel.clearList();
							stateSyncObject = new StateSource<DaqState>(iotClient, DaqState.class).getState();
					        iotConnected = true;
							connectionStatus.setText("Status: Connected");
						    connectButton.setText("Stop");
						    updateStateButton.setEnabled(true);
						} catch (AWSIotException e) {
							e.printStackTrace();
						}
					}
			}
		});
	}
	
	public void updateListener() {
		updateStateButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				String topic = null;
				Double gain = null;
				Double freq = null;
				if (!topicField.getText().equals("")) { //update topic
					topic = topicField.getText();
					sListener = new StrandListener(topic, AWSIotQos.QOS0, StrandWindow.this);
					try {
						iotClient.subscribe(sListener);
						listModel.clearList();
						topicStatus.setText("Current topic: " + topicField.getText());
						topicString = topicField.getText();
						topicField.setText("");
					} catch (AWSIotException e) {
						e.printStackTrace(); //TODO: fix all of these catch blocks to do something sensible
					}
				}

				if (!gainField.getText().equals("")) { //update gain
					gain = Math.abs(Double.parseDouble(gainField.getText()));
					gainStatus.setText("Current gain: " + gain.toString());
					gainField.setText("");
				}
				if (!frequencyField.getText().equals("")) { //update frequency
					freq = Math.abs(Double.parseDouble(frequencyField.getText()));
					frequencyStatus.setText("Current frequency: " + freq.toString() + " Hz");
					frequencyField.setText("");
				}
				
				stateSyncObject.update(topic, gain, freq);
			}
		});
	}
	
	public void writeLineToList(Sample sampleToWrite) {
		listModel.appendToList(sampleToWrite);
		if (scrollingCheckBox.isSelected()) {
			int lastIndex = listModel.getSize() - 1;
			if (lastIndex >= 0) {
			   listView.ensureIndexIsVisible(lastIndex);
			}
		}
	}
	
	private String getTableName() {
		File config = new File(configFile.getPath());
    	Scanner sc = null;
		String tableName;
		try {
			sc = new Scanner(config);
	    	String line = sc.nextLine();
	    	String[] fields = line.split("\\s+");
	    	if (fields.length != 2) {
	    		throw new IllegalArgumentException("invalid format for config file");
	    	}
	    	tableName = fields[1];    	
		} catch (FileNotFoundException e) {
			throw new IllegalArgumentException("bad filename for config file");
		} finally {
			sc.close();
		}
		return tableName;
	}
	
	public class AppendableView extends AbstractListModel<Sample> {
		private List<Sample> model;
		
		public AppendableView() {
			model = new ArrayList<Sample>();
		}
		
		public int getSize() {
			return model.size();
		}
		public Sample getElementAt(int index) {
			return model.get(index);
		}
		public void appendToList(Sample value) {
			model.add(value);
			fireIntervalAdded(this, this.getSize() - 1, this.getSize() - 1);
		}
		public void addBulkToList(List<Sample> listOfValues) {
			model.addAll(listOfValues);
			Collections.sort(model);
			fireIntervalAdded(this, 0, this.getSize() - 1);
		}
		public List<Sample> getList() {
			return model;
		}
		public void clearList() {
			int size = this.getSize();
			if (size > 0) {
				model.clear();
				fireIntervalRemoved(this, 0, size - 1);
			}
		}
	}


	
}