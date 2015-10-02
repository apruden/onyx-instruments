/*******************************************************************************
 * Copyright 2008(c) The OBiBa Consortium. All rights reserved.
 * 
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.obiba.onyx.jade.instrument.gemac800;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.obiba.onyx.jade.instrument.ExternalAppLauncherHelper;
import org.obiba.onyx.jade.instrument.service.InstrumentExecutionService;
import org.obiba.onyx.util.FileUtil;
import org.obiba.onyx.util.data.Data;
import org.obiba.onyx.util.data.DataBuilder;

public class CardiosoftInstrumentRunnerTest {

  private ExternalAppLauncherHelper externalAppHelper;

  private CardiosoftInstrumentRunner cardiosoftInstrumentRunner;

  private InstrumentExecutionService instrumentExecutionServiceMock;

  @Before
  public void setUp() throws URISyntaxException {

    // Skip tests when were not on Windows.
    Assume.assumeTrue(System.getProperty("os.name").toLowerCase().contains("windows"));

    cardiosoftInstrumentRunner = new CardiosoftInstrumentRunner();

    // Create a test directory to simulate Cardiosoft software installation path.
    String cardioSoftSimulatedPath = new File("target", "test-cardiosoft").getPath();
    (new File(cardioSoftSimulatedPath)).mkdir();
    cardiosoftInstrumentRunner.setCardioPath(cardioSoftSimulatedPath);

    // Cardiosoft database path.
    File databasePath = new File(cardioSoftSimulatedPath, "DATABASE");
    databasePath.mkdir();
    cardiosoftInstrumentRunner.setDatabasePath(databasePath.getPath());

    // Cardiosoft output path.
    File exportPath = new File(cardioSoftSimulatedPath, "EXPORT");
    exportPath.mkdir();
    cardiosoftInstrumentRunner.setExportPath(exportPath.getPath());

    cardiosoftInstrumentRunner.setSettingsFileName("CARDIO.INI");
    cardiosoftInstrumentRunner.setWinSettingsFileName("WIN.INI");
    cardiosoftInstrumentRunner.setEcgResourceBundle(ResourceBundle.getBundle("ecg-instrument", Locale.getDefault()));

    String resourcesParentDir = new File(getClass().getResource("/initecg/CARDIO.INI").toURI().getPath()).getParent();
    cardiosoftInstrumentRunner.setInitPath(new File(resourcesParentDir).getPath());

    // Cannot mock ExternalAppLauncherHelper (without EasyMock extension!),
    // so for now, use the class itself with the launch method overridden to
    // do nothing.
    externalAppHelper = new ExternalAppLauncherHelper() {
      public void launch() {
        // do nothing
      }
    };

    cardiosoftInstrumentRunner.setExternalAppHelper(externalAppHelper);

    // Create a mock instrumentExecutionService for testing.
    instrumentExecutionServiceMock = createMock(InstrumentExecutionService.class);
    cardiosoftInstrumentRunner.setInstrumentExecutionService(instrumentExecutionServiceMock);
    expect(instrumentExecutionServiceMock.getParticipantID()).andReturn("000000").anyTimes();
  }

  @Test
  public void testInitialize() throws FileNotFoundException, IOException, URISyntaxException {

    simulateResults();

    // Set arbitrary inputs for testing.
    Map<String, Data> inputData = new HashMap<String, Data>();
    inputData.put("INPUT_PARTICIPANT_BARCODE", DataBuilder.buildText("123456789"));
    inputData.put("INPUT_PARTICIPANT_LAST_NAME", DataBuilder.buildText("Tremblay"));
    inputData.put("INPUT_PARTICIPANT_FIRST_NAME", DataBuilder.buildText("Chantal"));
    inputData.put("INPUT_PARTICIPANT_GENDER", DataBuilder.buildText("FEMALE"));
    inputData.put("INPUT_PARTICIPANT_HEIGHT", DataBuilder.buildInteger(178));
    inputData.put("INPUT_PARTICIPANT_WEIGHT", DataBuilder.buildInteger(764));
    inputData.put("INPUT_PARTICIPANT_ETHNIC_GROUP", DataBuilder.buildText("CAUCASIAN"));
    inputData.put("INPUT_PARTICIPANT_BIRTH_YEAR", DataBuilder.buildInteger(1978));
    inputData.put("INPUT_PARTICIPANT_BIRTH_MONTH", DataBuilder.buildInteger(02));
    inputData.put("INPUT_PARTICIPANT_BIRTH_DAY", DataBuilder.buildInteger(25));
    inputData.put("INPUT_PARTICIPANT_PACEMAKER", DataBuilder.buildInteger(0));

    expect(instrumentExecutionServiceMock.getInputParametersValue("INPUT_PARTICIPANT_BARCODE", "INPUT_PARTICIPANT_LAST_NAME", "INPUT_PARTICIPANT_FIRST_NAME", "INPUT_PARTICIPANT_GENDER", "INPUT_PARTICIPANT_HEIGHT", "INPUT_PARTICIPANT_WEIGHT", "INPUT_PARTICIPANT_ETHNIC_GROUP", "INPUT_PARTICIPANT_BIRTH_YEAR", "INPUT_PARTICIPANT_BIRTH_MONTH", "INPUT_PARTICIPANT_BIRTH_DAY", "INPUT_PARTICIPANT_PACEMAKER")).andReturn(inputData);
    replay(instrumentExecutionServiceMock);

    cardiosoftInstrumentRunner.initialize();

    verify(instrumentExecutionServiceMock);

    verifyInitialization();

  }

  @Test
  public void testShutdown() throws FileNotFoundException, IOException, URISyntaxException {

    simulateResults();

    cardiosoftInstrumentRunner.shutdown();

    verifyInitialization();

  }

  @SuppressWarnings("unchecked")
  @Test
  public void testRun() throws Exception {

    simulateResults();

    externalAppHelper.launch();

    FileInputStream resultInputStream = new FileInputStream(new File(cardiosoftInstrumentRunner.getExportPath(), cardiosoftInstrumentRunner.getXmlFileName()));
    CardiosoftInstrumentResultParser resultParser = new CardiosoftInstrumentResultParser(resultInputStream);

    Object value;
    String paramName;

    // Load results properties file for testing.
    Properties testResults = new Properties();
    testResults.load(getClass().getResourceAsStream("/testResults.properties"));

    // Compare the test results file to the values extracted from the XML result file to make sure they match.
    for(PropertyDescriptor pd : Introspector.getBeanInfo(CardiosoftInstrumentResultParser.class).getPropertyDescriptors()) {

      paramName = pd.getName();
      value = pd.getReadMethod().invoke(resultParser);

      if(!paramName.equalsIgnoreCase("doc") && !paramName.equalsIgnoreCase("xpath") && !paramName.equalsIgnoreCase("xmldocument") && !paramName.equalsIgnoreCase("class")) {
        Assert.assertTrue(value.toString().equals(testResults.getProperty(paramName)));
      }

    }

    instrumentExecutionServiceMock.addOutputParameterValues((Map<String, Data>) anyObject());
    replay(instrumentExecutionServiceMock);

    // Make sure that the results are sent to the server.
    cardiosoftInstrumentRunner.sendDataToServer(resultParser);
    verify(instrumentExecutionServiceMock);

  }

  private void simulateResults() throws FileNotFoundException, IOException, URISyntaxException {

    // Create dummy BTrieve data file.
    FileOutputStream output = new FileOutputStream(new File(cardiosoftInstrumentRunner.getDatabasePath(), "btr-record.dat"));
    output.write((byte) 234432141);
    output.close();

    // Write some arbitrary data to simulate database access by Cardiosoft.
    (new FileOutputStream(new File(cardiosoftInstrumentRunner.getDatabasePath(), "PATIENT.BTR"))).write((byte) 234432141);
    (new FileOutputStream(new File(cardiosoftInstrumentRunner.getDatabasePath(), "EXAMINA.BTR"))).write((byte) 234432141);
    (new FileOutputStream(new File(cardiosoftInstrumentRunner.getCardioPath(), cardiosoftInstrumentRunner.getSettingsFileName()))).write((byte) 234432141);

    // Copy the results file + PDF file to test directory.
    FileUtil.copyFile(new File(getClass().getResource("/000000.XML").toURI()), new File(cardiosoftInstrumentRunner.getExportPath(), "000000.XML"));

  }

  private void verifyInitialization() {

    // Verify that the Cardiosoft database content has been cleared successfully.
    Assert.assertEquals(new File(cardiosoftInstrumentRunner.getInitPath(), "PATIENT.BTR").length(), new File(cardiosoftInstrumentRunner.getDatabasePath(), "PATIENT.BTR").length());
    Assert.assertEquals(new File(cardiosoftInstrumentRunner.getInitPath(), "EXAMINA.BTR").length(), new File(cardiosoftInstrumentRunner.getDatabasePath(), "EXAMINA.BTR").length());
    Assert.assertFalse(new File(cardiosoftInstrumentRunner.getDatabasePath(), "btr-record.dat").exists());

    // Make sure that Cardiosoft settings have been overwritten (cardio.ini).
    Assert.assertEquals(new File(cardiosoftInstrumentRunner.getInitPath(), cardiosoftInstrumentRunner.getSettingsFileName()).length(), new File(cardiosoftInstrumentRunner.getCardioPath(), "CARDIO.INI").length());

    // Make sure result files have been deleted.
    Assert.assertFalse(new File(cardiosoftInstrumentRunner.getExportPath(), cardiosoftInstrumentRunner.getXmlFileName()).exists());
  }
}
