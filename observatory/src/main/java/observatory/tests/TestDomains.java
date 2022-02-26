package observatory.tests;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import observatory.internetnlAPI.InternetnlAPI;
import observatory.internetnlAPI.InternetnlAPIException;
import observatory.internetnlAPI.TestIdNotFoundException;
import observatory.internetnlAPI.config.RequestType;
import observatory.internetnlAPI.config.TestInfo;
import observatory.internetnlAPI.config.results.TestResult;
import observatory.tests.collection.ListInfo;
import observatory.tests.collection.ListTestCollection;
import observatory.util.Util;

/**
 * A class to test the domains in a set of lists.
 */
public class TestDomains implements Closeable
{
    private static Logger log = Logger.getLogger(TestDomains.class.getSimpleName());

    private final InputStream domainsInputStream;
    private final Workbook domains;

    private final SortedSet<String> listsToTest;

    private final InternetnlAPI api;

    private final ListTestCollection listTestCollection;

    private final RequestType type;

    /**
     * Creates a new Test configuration.
     * 
     * @param domains - The file with the domains.
     * @param resultsFolder - The folder to output the results.
     * @param api - The Internet.nl API.
     * @param type - The type of test.
     * @throws IOException
     * @throws InvalidFormatException
     */
    public TestDomains(File domains, File resultsFolder, InternetnlAPI api, RequestType type)
        throws IOException, InvalidFormatException
    {
        this.domainsInputStream = new FileInputStream(domains);
        this.domains = Util.openWorkbook(this.domainsInputStream);
        this.type = type;
        this.listTestCollection = new ListTestCollection(resultsFolder, type);

        if (this.domains.getNumberOfSheets() == 0)
            throw new IllegalArgumentException("There is no domains to test.");

        this.listsToTest = new TreeSet<>();

        for (Sheet sheet : this.domains)
            this.listsToTest.add(sheet.getSheetName());

        this.api = Objects.requireNonNull(api);
    }

    /**
     * Creates a new Test configuration.
     * 
     * @param domains - The file with the domains.
     * @param resultsFolder - The folder to output the results.
     * @param api - The Internet.nl API.
     * @param type - The type of test.
     * @param listsToTest - The name of the lists to test.
     * @throws IOException
     * @throws InvalidFormatException
     */
    public TestDomains(File domains, File resultsFolder,
        InternetnlAPI api, RequestType type, Set<String> listsToTest) throws IOException, InvalidFormatException
    {
        this.domainsInputStream = new FileInputStream(domains);
        this.domains = Util.openWorkbook(this.domainsInputStream);
        this.type = type;
        this.listTestCollection = new ListTestCollection(resultsFolder, type);

        if (this.domains.getNumberOfSheets() == 0)
            throw new IllegalArgumentException("There is no domains to test.");

        this.listsToTest = checkListsToTest(Objects.requireNonNull(listsToTest), this.domains);
        this.api = Objects.requireNonNull(api);
    }

    /**
     * Check the lists to test.
     * 
     * @param listsToTest
     * @param domains
     * @return An ordered set of lists.
     */
    private static SortedSet<String> checkListsToTest(Set<String> listsToTest, Workbook domains)
    {
        SortedSet<String> toReturn =
        listsToTest.stream()
        .map(String::toUpperCase)
        .collect(Collectors.toCollection(() -> new TreeSet<String>()));

        for (String list : toReturn) {
            if (domains.getSheet(list) == null)
                throw new IllegalArgumentException(
                    String.format("There is no list named %s in the specified domains file.", list)
                );
        }

        return toReturn;
    }

    /**
     * Start testing.
     * 
     * @param overwrite - True to test the list if it was already tested.
     * 
     * @throws InternetnlAPIException
     * @throws IOException
     */
    public void start(boolean overwrite) throws InternetnlAPIException, IOException
    {
        for (String list : this.listsToTest)
            testList(list, overwrite);
    }

    /**
     * Test the domains in the specified list.
     * 
     * @param list - The name of the list
     * @param type - The type of test.
     * @param overwrite - True to test the list if it was already tested.
     * 
     * @throws InternetnlAPIException
     * @throws IOException
     */
    private void testList(String list, boolean overwrite)
        throws InternetnlAPIException, IOException
    {
        try
        {
            ListInfo info = listTestCollection.getListInfo(list);

            if (!overwrite && info != null && info.getOk())
            {
                log.info(String.format("Already tested list %s of type %s", list, type.getType()));
                return;
            }

            String[] domainsList = Util.getDomainsList(this.domains, list, this.type);

            if (overwrite || info == null)
            {
                RunningTest test = startTest(list, domainsList);
                waitAndSaveResults(test, list, domainsList);
            }
            else
            {
                //try to get the results of a previous test. If not, launch a new one.

                log.info(String.format("Already started %s test on list %s with test id: %s",
                    this.type.getType(), list, info.getTestId()));

                RunningTest test = new RunningTest(info.getTestId(), api);
                try
                {
                    waitAndSaveResults(test, list, domainsList);
                } catch (TestIdNotFoundException e) {
                    log.warning(e.getMessage());

                    // Previous test failed...
                    // Submit a new test and wait for the results.
                    test = startTest(list, domainsList);
                    waitAndSaveResults(test, list, domainsList);
                }
            }
        } catch (IOException | InternetnlAPIException e) {
            log.severe(String.format("An error occurred during %s test on list %s.",
                type.getType(), list));
            throw e;
        }
    }

    /**
     * Start the test associated to the specified list and type.
     * 
     * @param list - The name of the list
     * @param domainsList - The list of domains to test.
     * 
     * @return A new test.
     * @throws InternetnlAPIException
     */
    private RunningTest startTest(String list, String[] domainsList)
        throws IOException, InternetnlAPIException
    {
        log.info(String.format("Starting %s test on list %s", this.type.getType(), list));

        TestInfo testInfo = this.api.submit(list, domainsList, this.type);
        String testId = testInfo.getRequest().getRequest_id();
        log.info(String.format("Started %s test on list %s with id %s", type.getType(), list, testId));

        // print test ID in stderr
        System.err.println(testId);

        // save id in case of an error.
        listTestCollection.saveTestId(list, testId);

        return new RunningTest(testId, this.api);
    }

    /**
     * Waits and saves the results of a list test.
     * 
     * @param test - The test.
     * @param list - The name of the list.
     * @param domainsList - The list of domains.
     * @return The results of the test.
     * @throws IOException If an error occurred while saving the results.
     * @throws InternetnlAPIException
     */
    private ListTest waitAndSaveResults(RunningTest test, String list, String[] domainsList)
        throws IOException, InternetnlAPIException
    {
        log.info(String.format("Waiting for %s test on list %s", this.type.getType(), list));
        TestResult result = test.waitFor();
        log.info(String.format("Finished %s test on list %s and got results.", this.type.getType(), list));

        ListTest listResults = new ListTest(list, result, domainsList);
        listTestCollection.saveListResults(listResults);
        log.info(String.format("Successfully saved results of %s test on list %s.", this.type.getType(), list));

        return listResults;
    }

    @Override
    public void close() throws IOException
    {
        this.domainsInputStream.close();
        this.domains.close();
    }    
}
