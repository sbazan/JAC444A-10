/*
 * Created by JFormDesigner on Mon Apr 21 12:50:34 EDT 2008
 */

package Provider.GoogleMapsStatic.TestUI;

import Provider.GoogleMapsStatic.*;
import Task.*;
import Task.Manager.*;
import Task.ProgressMonitor.*;
import Task.Support.CoreSupport.*;
import Task.Support.GUISupport.*;
import com.jgoodies.forms.factories.*;
import info.clearthought.layout.*;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;

import javax.imageio.*;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.beans.*;
import java.text.*;
import java.util.Vector;
import java.util.concurrent.*;

/** @author nazmul idris */
public class SampleApp extends JFrame {
//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
// data members
//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
/** reference to task */
private SimpleTask _task;
/** this might be null. holds the image to display in a popup */
private BufferedImage _img;
/** this might be null. holds the text in case image doesn't display */
private String _respStr;

//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
// main method...
//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX

public static void main(String[] args) {
  Utils.createInEDT(SampleApp.class);
}

//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
// constructor
//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX

private void doInit() {
  GUIUtils.setAppIcon(this, "burn.png");
  GUIUtils.centerOnScreen(this);
  setVisible(true);

  int W = 28, H = W;
  boolean blur = false;
  float alpha = .7f;

  try {
    btnGetMap.setIcon(ImageUtils.loadScaledBufferedIcon("ok1.png", W, H, blur, alpha));
    btnQuit.setIcon(ImageUtils.loadScaledBufferedIcon("charging.png", W, H, blur, alpha));
  }
  catch (Exception e) {
    System.out.println(e);
  }

  _setupTask();
}

/** create a test task and wire it up with a task handler that dumps output to the textarea */
@SuppressWarnings("unchecked")
private void _setupTask() {

  TaskExecutorIF<ByteBuffer> functor = new TaskExecutorAdapter<ByteBuffer>() {
    public ByteBuffer doInBackground(Future<ByteBuffer> swingWorker,
                                     SwingUIHookAdapter hook) throws Exception
    {

      _initHook(hook);

      // set the license key
      MapLookup.setLicenseKey(ttfLicense.getText());
      // get the uri for the static map
      String uri = MapLookup.getMap(Double.parseDouble(ttfLat.getText()),
                                    Double.parseDouble(ttfLon.getText()),
                                    Integer.parseInt(ttfSizeW.getText()),
                                    Integer.parseInt(ttfSizeH.getText()),
                                    Integer.parseInt(ttfZoom.getText())
      );
      sout("Google Maps URI=" + uri);

      // get the map from Google
      GetMethod get = new GetMethod(uri);
      new HttpClient().executeMethod(get);

      ByteBuffer data = HttpUtils.getMonitoredResponse(hook, get);

      try {
        _img = ImageUtils.toCompatibleImage(ImageIO.read(data.getInputStream()));
        sout("converted downloaded data to image...");
      }
      catch (Exception e) {
        _img = null;
        sout("The URI is not an image. Data is downloaded, can't display it as an image.");
        _respStr = new String(data.getBytes());
      }

      return data;
    }

    @Override public String getName() {
      return _task.getName();
    }
  };

  _task = new SimpleTask(
      new TaskManager(),
      functor,
      "HTTP GET Task",
      "Download an image from a URL",
      AutoShutdownSignals.Daemon
  );

  _task.addStatusListener(new PropertyChangeListener() {
    public void propertyChange(PropertyChangeEvent evt) {
      sout(":: task status change - " + ProgressMonitorUtils.parseStatusMessageFrom(evt));
      lblProgressStatus.setText(ProgressMonitorUtils.parseStatusMessageFrom(evt));
    }
  });

  _task.setTaskHandler(new
      SimpleTaskHandler<ByteBuffer>() {
        @Override public void beforeStart(AbstractTask task) {
          sout(":: taskHandler - beforeStart");
        }
        @Override public void started(AbstractTask task) {
          sout(":: taskHandler - started ");
        }
        /** {@link SampleApp#_initHook} adds the task status listener, which is removed here */
        @Override public void stopped(long time, AbstractTask task) {
          sout(":: taskHandler [" + task.getName() + "]- stopped");
          sout(":: time = " + time / 1000f + "sec");
          task.getUIHook().clearAllStatusListeners();
        }
        @Override public void interrupted(Throwable e, AbstractTask task) {
          sout(":: taskHandler [" + task.getName() + "]- interrupted - " + e.toString());
        }
        @Override public void ok(ByteBuffer value, long time, AbstractTask task) {
          sout(":: taskHandler [" + task.getName() + "]- ok - size=" + (value == null
              ? "null"
              : value.toString()));
          if (_img != null) {
            _displayImgInFrame();
          }
          else _displayRespStrInFrame();

        }
        @Override public void error(Throwable e, long time, AbstractTask task) {
          sout(":: taskHandler [" + task.getName() + "]- error - " + e.toString());
        }
        @Override public void cancelled(long time, AbstractTask task) {
          sout(" :: taskHandler [" + task.getName() + "]- cancelled");
        }
      }
  );
}

private SwingUIHookAdapter _initHook(SwingUIHookAdapter hook) {
  hook.enableRecieveStatusNotification(checkboxRecvStatus.isSelected());
  hook.enableSendStatusNotification(checkboxSendStatus.isSelected());

  hook.setProgressMessage(ttfProgressMsg.getText());

  PropertyChangeListener listener = new PropertyChangeListener() {
    public void propertyChange(PropertyChangeEvent evt) {
      SwingUIHookAdapter.PropertyList type = ProgressMonitorUtils.parseTypeFrom(evt);
      int progress = ProgressMonitorUtils.parsePercentFrom(evt);
      String msg = ProgressMonitorUtils.parseMessageFrom(evt);

      progressBar.setValue(progress);
      progressBar.setString(type.toString());

      sout(msg);
    }
  };

  hook.addRecieveStatusListener(listener);
  hook.addSendStatusListener(listener);
  hook.addUnderlyingIOStreamInterruptedOrClosed(new PropertyChangeListener() {
    public void propertyChange(PropertyChangeEvent evt) {
      sout(evt.getPropertyName() + " fired!!!");
    }
  });

  return hook;
}

private void _displayImgInFrame() {

  final JFrame frame = new JFrame("Google Static Map");
  GUIUtils.setAppIcon(frame, "71.png");
  frame.setDefaultCloseOperation(DISPOSE_ON_CLOSE);

  JLabel imgLbl = new JLabel(new ImageIcon(_img));
  imgLbl.setToolTipText(MessageFormat.format("<html>Image downloaded from URI<br>size: w={0}, h={1}</html>",
                                             _img.getWidth(), _img.getHeight()));
  imgLbl.addMouseListener(new MouseListener() {
    public void mouseClicked(MouseEvent e) {}
    public void mousePressed(MouseEvent e) { frame.dispose();}
    public void mouseReleased(MouseEvent e) { }
    public void mouseEntered(MouseEvent e) { }
    public void mouseExited(MouseEvent e) { }
  });

  frame.setContentPane(imgLbl);
  frame.pack();

  GUIUtils.centerOnScreen(frame);
  frame.setVisible(true);
}

private void _displayRespStrInFrame() {

  final JFrame frame = new JFrame("Google Static Map - Error");
  GUIUtils.setAppIcon(frame, "69.png");
  frame.setDefaultCloseOperation(DISPOSE_ON_CLOSE);

  JTextArea response = new JTextArea(_respStr, 25, 80);
  response.addMouseListener(new MouseListener() {
    public void mouseClicked(MouseEvent e) {}
    public void mousePressed(MouseEvent e) { frame.dispose();}
    public void mouseReleased(MouseEvent e) { }
    public void mouseEntered(MouseEvent e) { }
    public void mouseExited(MouseEvent e) { }
  });

  frame.setContentPane(new JScrollPane(response));
  frame.pack();

  GUIUtils.centerOnScreen(frame);
  frame.setVisible(true);
}

/** simply dump status info to the textarea */
private void sout(final String s) {
  Runnable soutRunner = new Runnable() {
    public void run() {
      if (ttaStatus.getText().equals("")) {
        ttaStatus.setText(s);
      }
      else {
        ttaStatus.setText(ttaStatus.getText() + "\n" + s);
      }
    }
  };

  if (ThreadUtils.isInEDT()) {
    soutRunner.run();
  }
  else {
    SwingUtilities.invokeLater(soutRunner);
  }
}

private void startTaskAction() {
  try {
    _task.execute();
  }
  catch (TaskException e) {
    sout(e.getMessage());
  }
}


public SampleApp() {
  initComponents();
  doInit();
}

private void quitProgram() {
  _task.shutdown();
  System.exit(0);
}

private void initComponents() {
  // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
  // Generated using JFormDesigner non-commercial license
  dialogPane = new JPanel();
  contentPanel = new JPanel();
  panel1 = new JPanel();
  label2 = new JLabel();
  ttfSizeW = new JTextField();
  label4 = new JLabel();
  ttfLat = new JTextField();
  
  // Included two buttons for Latitude
  btnLatInc = new JButton();
  btnLatDec = new JButton();
  
  btnGetMap = new JButton();
  label3 = new JLabel();
  ttfSizeH = new JTextField();
  label5 = new JLabel();
  ttfLon = new JTextField();
  
  // Included two buttons for Longitude
  btnLonInc = new JButton();
  btnLonDec = new JButton();
  
  btnQuit = new JButton();
  label1 = new JLabel();
  ttfLicense = new JTextField();
  label6 = new JLabel();
  ttfZoom = new JTextField();
  
  // Included a combo box, label, and list
  // of capitals for Canada and US
  label7 = new JLabel();
  country = new JComboBox(countries);
  country.setEditable( false );
  country.setMaximumRowCount( 5 );
  country.insertItemAt( "Select a Country", 0 );
  country.setSelectedIndex(0);
  
  label8 = new JLabel();
  capital = new JComboBox();
  
  scrollPane1 = new JScrollPane();
  ttaStatus = new JTextArea();
  panel2 = new JPanel();
  panel3 = new JPanel();
  checkboxRecvStatus = new JCheckBox();
  checkboxSendStatus = new JCheckBox();
  ttfProgressMsg = new JTextField();
  progressBar = new JProgressBar();
  lblProgressStatus = new JLabel();

  //======== this ========
  setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
  setTitle("Google Static Maps");
  setIconImage(null);
  Container contentPane = getContentPane();
  contentPane.setLayout(new BorderLayout());

  //======== dialogPane ========
  {
  	dialogPane.setBorder(new EmptyBorder(12, 12, 12, 12));
  	dialogPane.setOpaque(false);
  	dialogPane.setLayout(new BorderLayout());

  	//======== contentPanel ========
  	{
  		contentPanel.setOpaque(false);
  		contentPanel.setLayout(new TableLayout(new double[][] {
  			{TableLayout.FILL},
  			{TableLayout.PREFERRED, TableLayout.FILL, TableLayout.PREFERRED}}));
  		((TableLayout)contentPanel.getLayout()).setHGap(5);
  		((TableLayout)contentPanel.getLayout()).setVGap(5);

  		//======== panel1 ========
  		{
  			panel1.setOpaque(false);
  			panel1.setBorder(new CompoundBorder(
  				new TitledBorder("Configure the inputs to Google Static Maps"),
  				Borders.DLU2_BORDER));
  			panel1.setLayout(new TableLayout(new double[][] {
  				{0.13, 0.20, 0.12, 0.25, 0.05, 0.05, 0.20, TableLayout.FILL},
  				{TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.PREFERRED}}));
  			((TableLayout)panel1.getLayout()).setHGap(5);
  			((TableLayout)panel1.getLayout()).setVGap(5);

  			//---- label2 ----
  			label2.setText("Size Width");
  			label2.setHorizontalAlignment(SwingConstants.RIGHT);
  			panel1.add(label2, new TableLayoutConstraints(0, 0, 0, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			//---- ttfSizeW ----
  			ttfSizeW.setText("512");
  			panel1.add(ttfSizeW, new TableLayoutConstraints(1, 0, 1, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			//---- label4 ----
  			label4.setText("Latitude");
  			label4.setHorizontalAlignment(SwingConstants.RIGHT);
  			panel1.add(label4, new TableLayoutConstraints(2, 0, 2, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			//---- ttfLat ----
  			// Included an increase and decrease button for Latitude
  			ttfLat.setText("38.931099");
  			btnLatInc.setText("+");
  			btnLatInc.addActionListener(new ActionListener() {
  				public void actionPerformed(ActionEvent e) {
  					ttfLat.setText("" + (Double.parseDouble(ttfLat.getText ()) + 1));
  				}
  			});
  			btnLatDec.setText("-");
  			btnLatDec.addActionListener(new ActionListener() {
  				public void actionPerformed(ActionEvent e) {
  					ttfLat.setText("" + (Double.parseDouble(ttfLat.getText ()) - 1));
  				}
  			});
  			panel1.add(ttfLat, new TableLayoutConstraints(3, 0, 3, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
  			panel1.add(btnLatInc, new TableLayoutConstraints(4, 0, 4, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
  			panel1.add(btnLatDec, new TableLayoutConstraints(5, 0, 5, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
  			
  			//---- btnGetMap ----
  			btnGetMap.setText("Get Map");
  			btnGetMap.setHorizontalAlignment(SwingConstants.LEFT);
  			btnGetMap.setMnemonic('G');
  			btnGetMap.addActionListener(new ActionListener() {
  				public void actionPerformed(ActionEvent e) {
  					startTaskAction();
  				}
  			});
  			panel1.add(btnGetMap, new TableLayoutConstraints(6, 0, 6, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			//---- label3 ----
  			label3.setText("Size Height");
  			label3.setHorizontalAlignment(SwingConstants.RIGHT);
  			panel1.add(label3, new TableLayoutConstraints(0, 1, 0, 1, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			//---- ttfSizeH ----
  			ttfSizeH.setText("512");
  			panel1.add(ttfSizeH, new TableLayoutConstraints(1, 1, 1, 1, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			//---- label5 ----
  			label5.setText("Longitude");
  			label5.setHorizontalAlignment(SwingConstants.RIGHT);
  			panel1.add(label5, new TableLayoutConstraints(2, 1, 2, 1, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			//---- ttfLon ----
  			// Included an increase and decrease button for Longitude
  			ttfLon.setText("-77.3489");
  			btnLonInc.setText("+");
  			btnLonInc.addActionListener(new ActionListener() {
  				public void actionPerformed(ActionEvent e) {
  					ttfLon.setText("" + (Double.parseDouble(ttfLon.getText ()) + 1));
  				}
  			});
  			btnLonDec.setText("-");
  			btnLonDec.addActionListener(new ActionListener() {
  				public void actionPerformed(ActionEvent e) {
  					ttfLon.setText("" + (Double.parseDouble(ttfLon.getText ()) - 1));
  				}
  			});
  			panel1.add(ttfLon, new TableLayoutConstraints(3, 1, 3, 1, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
  			panel1.add(btnLonInc, new TableLayoutConstraints(4, 1, 4, 1, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
  			panel1.add(btnLonDec, new TableLayoutConstraints(5, 1, 5, 1, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
  			
  			//---- btnQuit ----
  			btnQuit.setText("Quit");
  			btnQuit.setMnemonic('Q');
  			btnQuit.setHorizontalAlignment(SwingConstants.LEFT);
  			btnQuit.setHorizontalTextPosition(SwingConstants.RIGHT);
  			btnQuit.addActionListener(new ActionListener() {
  				public void actionPerformed(ActionEvent e) {
  					quitProgram();
  				}
  			});
  			panel1.add(btnQuit, new TableLayoutConstraints(6, 1, 6, 1, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			//---- label1 ----
  			//label1.setText("License Key");
  			//label1.setHorizontalAlignment(SwingConstants.RIGHT);
  			//panel1.add(label1, new TableLayoutConstraints(0, 2, 0, 2, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			//---- ttfLicense ----
  			//ttfLicense.setToolTipText("Enter your own URI for a file to download in the background");
  			//panel1.add(ttfLicense, new TableLayoutConstraints(1, 2, 1, 2, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			//---- label6 ----
  			label6.setText("<- Zoom [0-19]");
  			label6.setHorizontalAlignment(SwingConstants.LEFT);
  			panel1.add(label6, new TableLayoutConstraints(6, 2, 6, 2, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			//---- ttfZoom ----
  			ttfZoom.setText("14");
  			panel1.add(ttfZoom, new TableLayoutConstraints(5, 2, 5, 2, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
  			
  			// Included a label for countries
  			//---- label7 ----
  			label7.setText("Countries");
  			label7.setHorizontalAlignment(SwingConstants.RIGHT);
  			panel1.add(label7, new TableLayoutConstraints(0, 2, 0, 2, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
  			
  			// Included a label for cities
  			//---- label8 ----
  			label8.setText("Capitals");
  			label8.setHorizontalAlignment(SwingConstants.RIGHT);
  			panel1.add(label8, new TableLayoutConstraints(2, 2, 2, 2, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			// Included a combo box for countries
  			panel1.add(country, new TableLayoutConstraints(1, 2, 1, 2, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
  			
  			// Included a combo box for capitals
  			// Default setting for capital combo box
  			capital.setEditable(false);
			capital.setMaximumRowCount(5);
			capital.insertItemAt("Select a Capital City", 0);
			capital.setSelectedIndex(0);
  			panel1.add(capital, new TableLayoutConstraints(3, 2, 3, 2, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			//---- country -----
  			country.addActionListener(new ActionListener() {
  				public void actionPerformed(ActionEvent e) {
  					// If Canada was selected, display its capitals
  					if(country.getSelectedIndex() == 1){
  						// Remove all content first
  						capital.removeAllItems();
  						
  						capital.insertItemAt("Select a Capital City", 0);
  					    capital.setSelectedIndex(0);
  					    
  					    // Add all content from canada
  						for(int i = 1; i <= canada.length; i++)	
  						  capital.addItem(canada[i -1]);
  					}
  					// If United States was selected, display its capitals
  				    if(country.getSelectedIndex() == 2){
  				    	// Remove all content first
  				    	capital.removeAllItems();
  				    	
  				    	capital.insertItemAt("Select a Capital City", 0);
  					    capital.setSelectedIndex(0);
  					    
  					    // Add all content from unitedStates
  				    	for(int i = 1; i <= unitedStates.length; i++)
  				    		capital.addItem(unitedStates[i -1]); 			  			
  					}
  				}
  			});

  			//---- capital ----
  			capital.addActionListener(new ActionListener() {
  				public void actionPerformed(ActionEvent e) {
  					// Set latitude and longitude values for each capital from Canada
  					if(country.getSelectedIndex() == 1){
  						// Charlottetown
  						if(capital.getSelectedIndex() == 1){
  							ttfLat.setText("46.247847");
  							ttfLon.setText("-63.12021");
  						}
  						// Edmonton
  						else if(capital.getSelectedIndex() == 2){
  							ttfLat.setText("53.543564");
  							ttfLon.setText("-113.490452");
  						}
  						// Fredericton
  						else if(capital.getSelectedIndex() == 3){
  							ttfLat.setText("45.954364");
  							ttfLon.setText("-66.645619");
  						}
  						// Halifax
  						else if(capital.getSelectedIndex() == 4){
  							ttfLat.setText("44.648881");
  							ttfLon.setText("-63.575312");
  						}
  						// Iqaluit
  						else if(capital.getSelectedIndex() == 5){
  							ttfLat.setText("63.746693");
  							ttfLon.setText("-68.5169669");
  						}
  						// Ottawa
  						else if(capital.getSelectedIndex() == 6){
  							ttfLat.setText("45.411572");
  							ttfLon.setText("-75.698194");
  						}
  						// Quebec City
  						else if(capital.getSelectedIndex() == 7){
  							ttfLat.setText("46.802071");
  							ttfLon.setText("-71.244926");
  						}
  						// Regina
  						else if(capital.getSelectedIndex() == 8){
  							ttfLat.setText("50.447234");
  							ttfLon.setText("-104.618013");
  						}
  						// St. John's
  						else if(capital.getSelectedIndex() == 9){
  							ttfLat.setText("47.561485");
  							ttfLon.setText("-52.712675");
  						}
  						// Toronto
  						else if(capital.getSelectedIndex() == 10){
  							ttfLat.setText("43.6525");
  							ttfLon.setText("-79.3816667");
  						}
  						// Victoria 
  						else if(capital.getSelectedIndex() == 11){
  							ttfLat.setText("48.4275");
  							ttfLon.setText("-123.367259");
  						}
  						// Whitehorse
  						else if(capital.getSelectedIndex() == 12){
  							ttfLat.setText("60.7166667");
  							ttfLon.setText("-135.05");
  						}
  						// Winnepeg
  						else if(capital.getSelectedIndex() == 13){
  							ttfLat.setText("49.8997541");
  							ttfLon.setText("-97.1374937");
  						}
  						// Yellowknife
  						else if(capital.getSelectedIndex() == 14){
  							ttfLat.setText("62.454648");
  							ttfLon.setText("-114.376469");
  						}
  					 						
  					}
  					// Set latitude and longitude values for each capital from US
  					if(country.getSelectedIndex() == 2){
  						// Albany
  						if(capital.getSelectedIndex() == 1){
  							ttfLat.setText("42.6525793");
  							ttfLon.setText("-73.7562317");
  						}
  						// Annapolis
  						else if(capital.getSelectedIndex() == 2){
  							ttfLat.setText("38.9784453");
  							ttfLon.setText("-76.4921829");
  						}
  						// Atlanta
  						else if(capital.getSelectedIndex() == 3){
  							ttfLat.setText("33.7489954");
  							ttfLon.setText("-84.3879824");
  						}
  						// Augusta
  						else if(capital.getSelectedIndex() == 4){
  							ttfLat.setText("44.3106241");
  							ttfLon.setText("-69.7794897");
  						}
  						// Austin
  						else if(capital.getSelectedIndex() == 5){
  							ttfLat.setText("30.267153");
  							ttfLon.setText("-97.7430608");
  						}
  						// Baton Rouge
  						else if(capital.getSelectedIndex() == 6){
  							ttfLat.setText("30.4507462");
  							ttfLon.setText("-91.154551");
  						}
  						// Bismarck
  						else if(capital.getSelectedIndex() == 7){
  							ttfLat.setText("46.8083268");
  							ttfLon.setText("-100.7837392");
  						}
  						// Boise
  						else if(capital.getSelectedIndex() == 8){
  							ttfLat.setText("43.612631");
  							ttfLon.setText("-116.211076");
  						}
  						// Boston
  						else if(capital.getSelectedIndex() == 9){
  							ttfLat.setText("42.3584308");
  							ttfLon.setText("-71.0597732");
  						}
  						// Carson City
  						else if(capital.getSelectedIndex() == 10){
  							ttfLat.setText("39.1637984");
  							ttfLon.setText("-119.7674034");
  						}
  						// Charleston
  						else if(capital.getSelectedIndex() == 11){
  							ttfLat.setText("38.3498195");
  							ttfLon.setText("-81.6326234");
  						}
  						// Cheyenne
  						else if(capital.getSelectedIndex() == 12){
  							ttfLat.setText("41.1399814");
  							ttfLon.setText("-104.8202462");
  						}
  						// Columbia
  						else if(capital.getSelectedIndex() == 13){
  							ttfLat.setText("34.0007104");
  							ttfLon.setText("-81.0348144");
  						}
  						// Columbus
  						else if(capital.getSelectedIndex() == 14){
  							ttfLat.setText("39.9611755");
  							ttfLon.setText("-82.9987942");
  						}
  						// Concord
  						else if(capital.getSelectedIndex() == 15){
  							ttfLat.setText("43.2081366");
  							ttfLon.setText("-71.5375718");
  						}
  						// Denver
  						else if(capital.getSelectedIndex() == 16){
  							ttfLat.setText("39.7391536");
  							ttfLon.setText("-104.9847034");
  						}
  						// Des Moines
  						else if(capital.getSelectedIndex() == 17){
  							ttfLat.setText("41.6005448");
  							ttfLon.setText("-93.6091064");
  						}
  						// Dover
  						else if(capital.getSelectedIndex() == 18){
  							ttfLat.setText("39.158168");
  							ttfLon.setText("-75.5243682");
  						}
  						// Frankfort
  						else if(capital.getSelectedIndex() == 19){
  							ttfLat.setText("38.2009055");
  							ttfLon.setText("-84.8732835");
  						}
  						// Harrisburg
  						else if(capital.getSelectedIndex() == 20){
  							ttfLat.setText("40.2737002");
  							ttfLon.setText("-76.8844179");
  						}
  						// Hartford
  						else if(capital.getSelectedIndex() == 21){
  							ttfLat.setText("41.7637111");
  							ttfLon.setText("-72.6850932");
  						}
  						// Helena
  						else if(capital.getSelectedIndex() == 22){
  							ttfLat.setText("46.5958056");
  							ttfLon.setText("-112.0270306");
  						}
  						// Honolulu
  						else if(capital.getSelectedIndex() == 23){
  							ttfLat.setText("21.3069444");
  							ttfLon.setText("-157.8583333");
  						}
  						// Indianapolis
  						else if(capital.getSelectedIndex() == 24){
  							ttfLat.setText("39.7683765");
  							ttfLon.setText("-86.1580423");
  						}
  						// Jackson
  						else if(capital.getSelectedIndex() == 25){
  							ttfLat.setText("32.2987573");
  							ttfLon.setText("-90.1848103");
  						}
  						// Jefferson City
  						else if(capital.getSelectedIndex() == 26){
  							ttfLat.setText("38.5767017");
  							ttfLon.setText("-92.1735164");
  						}
  						// Juneau
  						else if(capital.getSelectedIndex() == 27){
  							ttfLat.setText("58.3019444");
  							ttfLon.setText("-134.4197222");
  						}
  						// Lansing
  						else if(capital.getSelectedIndex() == 28){
  							ttfLat.setText("42.732535");
  							ttfLon.setText("-84.5555347");
  						}
  						// Lincoln
  						else if(capital.getSelectedIndex() == 29){
  							ttfLat.setText("40.806862");
  							ttfLon.setText("-96.681679");
  						}
  						// Little Rock
  						else if(capital.getSelectedIndex() == 30){
  							ttfLat.setText("34.7464809");
  							ttfLon.setText("-92.2895948");
  						}
  						// Madison
  						else if(capital.getSelectedIndex() == 31){
  							ttfLat.setText("43.0730517");
  							ttfLon.setText("-89.4012302");
  						}
  						// Montgomery
  						else if(capital.getSelectedIndex() == 32){
  							ttfLat.setText("32.3668052");
  							ttfLon.setText("-86.2999689");
  						}
  						// Montpelier
  						else if(capital.getSelectedIndex() == 33){
  							ttfLat.setText("44.2600593");
  							ttfLon.setText("-72.5753869");
  						}
  						// Nashville
  						else if(capital.getSelectedIndex() == 34){
  							ttfLat.setText("36.1658899");
  							ttfLon.setText("-86.7844432");
  						}
  						// Oklahoma City
  						else if(capital.getSelectedIndex() == 35){
  							ttfLat.setText("35.4675602");
  							ttfLon.setText("-97.5164276");
  						}
  						// Olympia
  						else if(capital.getSelectedIndex() == 36){
  							ttfLat.setText("47.0378741");
  							ttfLon.setText("-122.9006951");
  						}
  						// Phoenix
  						else if(capital.getSelectedIndex() == 37){
  							ttfLat.setText("33.4483771");
  							ttfLon.setText("-112.0740373");
  						}
  						// Pierre
  						else if(capital.getSelectedIndex() == 38){
  							ttfLat.setText("44.3683156");
  							ttfLon.setText("-100.3509665");
  						}
  						// Providence
  						else if(capital.getSelectedIndex() == 39){
  							ttfLat.setText("41.8239891");
  							ttfLon.setText("-71.4128343");
  						}
  						// Raleigh
  						else if(capital.getSelectedIndex() == 40){
  							ttfLat.setText("35.772096");
  							ttfLon.setText("-78.6386145");
  						}
  						// Richmond
  						else if(capital.getSelectedIndex() == 41){
  							ttfLat.setText("37.540970");
  							ttfLon.setText("-77.442884");
  						}
  						// Sacramento
  						else if(capital.getSelectedIndex() == 42){
  							ttfLat.setText("38.5815719");
  							ttfLon.setText("-121.4943996");
  						}
  						// Saint Paul
  						else if(capital.getSelectedIndex() == 43){
  							ttfLat.setText("44.9541667");
  							ttfLon.setText("-93.1138889");
  						}
  						// Salem
  						else if(capital.getSelectedIndex() == 44){
  							ttfLat.setText("44.9428975");
  							ttfLon.setText("-123.0350963");
  						}
  						// Salt Lake City
  						else if(capital.getSelectedIndex() == 45){
  							ttfLat.setText("40.7607793");
  							ttfLon.setText("-111.8910474");
  						}
  						// Santa Fe
  						else if(capital.getSelectedIndex() == 46){
  							ttfLat.setText("35.6869752");
  							ttfLon.setText("-105.937799");
  						}
  						// Springfield
  						else if(capital.getSelectedIndex() == 47){
  							ttfLat.setText("39.7817213");
  							ttfLon.setText("-89.6501481");
  						}
  						// Tallahassee
  						else if(capital.getSelectedIndex() == 48){
  							ttfLat.setText("30.4382559");
  							ttfLon.setText("-84.2807329");
  						}
  						// Topeka
  						else if(capital.getSelectedIndex() == 49){
  							ttfLat.setText("39.0558235");
  							ttfLon.setText("-95.6890185");
  						}
  						// Trenton
  						else if(capital.getSelectedIndex() == 50){
  							ttfLat.setText("40.2170534");
  							ttfLon.setText("-74.7429384");
  						}
  					}
  				}
  			});
  			
  		}
  		contentPanel.add(panel1, new TableLayoutConstraints(0, 0, 0, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  		//======== scrollPane1 ========
  		{
  			scrollPane1.setBorder(new TitledBorder("System.out - displays all status and progress messages, etc."));
  			scrollPane1.setOpaque(false);

  			//---- ttaStatus ----
  			ttaStatus.setBorder(Borders.createEmptyBorder("1dlu, 1dlu, 1dlu, 1dlu"));
  			ttaStatus.setToolTipText("<html>Task progress updates (messages) are displayed here,<br>along with any other output generated by the Task.<html>");
  			scrollPane1.setViewportView(ttaStatus);
  		}
  		contentPanel.add(scrollPane1, new TableLayoutConstraints(0, 1, 0, 1, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  		//======== panel2 ========
  		{
  			panel2.setOpaque(false);
  			panel2.setBorder(new CompoundBorder(
  				new TitledBorder("Status - control progress reporting"),
  				Borders.DLU2_BORDER));
  			panel2.setLayout(new TableLayout(new double[][] {
  				{0.45, TableLayout.FILL, 0.45},
  				{TableLayout.PREFERRED, TableLayout.PREFERRED}}));
  			((TableLayout)panel2.getLayout()).setHGap(5);
  			((TableLayout)panel2.getLayout()).setVGap(5);

  			//======== panel3 ========
  			{
  				panel3.setOpaque(false);
  				panel3.setLayout(new GridLayout(1, 2));

  				//---- checkboxRecvStatus ----
  				checkboxRecvStatus.setText("Enable \"Recieve\"");
  				checkboxRecvStatus.setOpaque(false);
  				checkboxRecvStatus.setToolTipText("Task will fire \"send\" status updates");
  				checkboxRecvStatus.setSelected(true);
  				panel3.add(checkboxRecvStatus);

  				//---- checkboxSendStatus ----
  				checkboxSendStatus.setText("Enable \"Send\"");
  				checkboxSendStatus.setOpaque(false);
  				checkboxSendStatus.setToolTipText("Task will fire \"recieve\" status updates");
  				panel3.add(checkboxSendStatus);
  			}
  			panel2.add(panel3, new TableLayoutConstraints(0, 0, 0, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			//---- ttfProgressMsg ----
  			ttfProgressMsg.setText("Loading map from Google Static Maps");
  			ttfProgressMsg.setToolTipText("Set the task progress message here");
  			panel2.add(ttfProgressMsg, new TableLayoutConstraints(2, 0, 2, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			//---- progressBar ----
  			progressBar.setStringPainted(true);
  			progressBar.setString("progress %");
  			progressBar.setToolTipText("% progress is displayed here");
  			panel2.add(progressBar, new TableLayoutConstraints(0, 1, 0, 1, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			//---- lblProgressStatus ----
  			lblProgressStatus.setText("task status listener");
  			lblProgressStatus.setHorizontalTextPosition(SwingConstants.LEFT);
  			lblProgressStatus.setHorizontalAlignment(SwingConstants.LEFT);
  			lblProgressStatus.setToolTipText("Task status messages are displayed here when the task runs");
  			panel2.add(lblProgressStatus, new TableLayoutConstraints(2, 1, 2, 1, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
  		}
  		contentPanel.add(panel2, new TableLayoutConstraints(0, 2, 0, 2, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
  	}
  	dialogPane.add(contentPanel, BorderLayout.CENTER);
  }
  contentPane.add(dialogPane, BorderLayout.CENTER);
  setSize(675, 485);
  setLocationRelativeTo(null);
  // JFormDesigner - End of component initialization  //GEN-END:initComponents
}

// JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables
// Generated using JFormDesigner non-commercial license
private JPanel dialogPane;
private JPanel contentPanel;
private JPanel panel1;
private JLabel label2;
private JTextField ttfSizeW;
private JLabel label4;
private JTextField ttfLat;

// Included two buttons for Latitude
private JButton btnLatInc;
private JButton btnLatDec;

private JButton btnGetMap;
private JLabel label3;
private JTextField ttfSizeH;
private JLabel label5;
private JTextField ttfLon;

// Included two buttons for Longitude
private JButton btnLonInc;
private JButton btnLonDec;

private JButton btnQuit;
private JLabel label1;
private JTextField ttfLicense;
private JLabel label6;
private JTextField ttfZoom;

// Included a combo box, label, and list
// of all capitals for Canada and US
private JLabel label7;
private JComboBox country;
private String[] countries = {"Canada", "United States"};
private JLabel label8;
private JComboBox capital;
private String[] canada = {"Charlottetown", "Edmonton", "Fredericton", "Halifax", "Iqaluit", 
		                   "Ottawa", "Quebec City", "Regina", "St. John's", "Toronto", 
		                    "Victoria", "Whitehorse", "Winnepeg", "Yellowknife"};
private String[] unitedStates = {"Albany", "Annapolis", "Atlanta", "Augusta", "Austin", 
		                         "Baton Rouge", "Bismarck", "Boise", "Boston", "Carson City",
		                         "Charleston", "Cheyenne", "Columbia", "Columbus", "Concord",
		                         "Denver", "Des Moines", "Dover", "Frankfort", "Harrisburg",
		                         "Hartford", "Helena", "Honolulu", "Indianapolis", "Jackson", 
		                         "Jefferson City", "Juneau", "Lansing", "Lincoln", "Little Rock", 
		                         "Madison", "Montgomery", "Montpelier", "Nashville", "Oklahoma City", 
		                         "Olympia", "Phoenix", "Pierre", "Providence", "Raleigh", 
		                         "Richmond", "Sacramento", "Saint Pual", "Salem", "Salt Lake City", 
		                         "Santa Fe", "Springfield", "Tallahassee", "Topeka", "Trenton"};

private JScrollPane scrollPane1;
private JTextArea ttaStatus;
private JPanel panel2;
private JPanel panel3;
private JCheckBox checkboxRecvStatus;
private JCheckBox checkboxSendStatus;
private JTextField ttfProgressMsg;
private JProgressBar progressBar;
private JLabel lblProgressStatus;
// JFormDesigner - End of variables declaration  //GEN-END:variables
}
