package observatory.argsParser;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import observatory.argsParser.options.Option;
import observatory.argsParser.options.OptionType;
import observatory.argsParser.options.OptionValue;
import observatory.argsParser.options.ParseOptions;
import observatory.internetnlAPI.config.RequestType;
import observatory.util.Util;

public class ReportArgs
{
    private static final String WEB_TEMPLATE_FILE = "template-web.xlsx";
    private static final String MAIL_TEMPLATE_FILE = "template-mail.xlsx";

    private static final String DATE_FORMAT = "dd/MM/yyyy";

    public static final Option OPTION_WORKING_DIR = new Option("--dir", OptionType.SINGLE);
    public static final Option OPTION_TEMPLATE_FILE = new Option("--template", OptionType.SINGLE);
    public static final Option OPTION_DATE = new Option("--date", OptionType.SINGLE);
    public static final Option OPTION_FULL_REPORT = new Option("--full-report", OptionType.LIST);

    private static final ParseOptions PARSE_OPTIONS = new ParseOptions(
            Set.of(OPTION_WORKING_DIR, OPTION_TEMPLATE_FILE, OPTION_DATE, OPTION_FULL_REPORT));

    private final RequestType type;

    private final Map<Option, OptionValue> options;

    private final File reportFile;

    private final List<File> listsResultFiles;


    private File workingDir, templateFile;
    private Calendar date;
    private List<String> listsFullReport;


    public ReportArgs(List<String> args) throws ParserException {
        if (args.isEmpty())
            throw new ParserException("Not enough arguments.");

        this.type = RequestType.parseType(args.remove(0));
        this.options = PARSE_OPTIONS.parse(args);
        this.reportFile = parseReportFile(args);
        this.listsResultFiles = parseResultFiles(args);

        args.clear();
    }

    private static File parseReportFile(List<String> args) throws ParserException
    {
        if (args.isEmpty())
            throw new ParserException("Not enough arguments.");

        return new File(args.remove(0));
    }

    private static List<File> parseResultFiles(List<String> args) throws ParserException
    {
        if (args.isEmpty())
            throw new ParserException("Not enough arguments.");

        return args.stream().map(File::new).collect(Collectors.toUnmodifiableList());
    }

    /**
     * @return the type
     */
    public RequestType getType() {
        return type;
    }

    /**
     * @return the reportFile
     */
    public File getReportFile() {
        return reportFile;
    }

    /**
     * @return the listsResultFiles
     */
    public List<File> getListsResultFiles() {
        return listsResultFiles;
    }

    //#region Options

    public File getWorkingDir()
    {
        if (this.workingDir == null)
            this.workingDir = getOption(OPTION_WORKING_DIR, (Function<OptionValue, File>)
                (optionValue) ->
                {
                    return new File(optionValue.getSingle());
                },
                Util::getCurrentWorkingDir);

        return this.workingDir;
    }

    public File getTemplateFile()
    {
        if (this.templateFile == null)
            this.templateFile = getOption(OPTION_TEMPLATE_FILE, (Function<OptionValue, File>)
                (optionValue) ->
                {
                    return new File(optionValue.getSingle());
                },
                () -> new File(getWorkingDir(),
                        this.type == RequestType.WEB ? WEB_TEMPLATE_FILE : MAIL_TEMPLATE_FILE));

        return this.templateFile;
    }

    public Calendar getReportDate() throws ParserException
    {
        if (this.date == null)
            this.date = getOption(OPTION_DATE, (ParseValueFunction<Calendar>)
                (optionValue) ->
                {
                    return parseDate(optionValue.getSingle());
                },
                Calendar::getInstance);

        return this.date;
    }

    public List<String> getListsFullReport()
    {
        if (this.listsFullReport == null)
            this.listsFullReport = getOption(OPTION_FULL_REPORT,
                (Function<OptionValue, List<String>>) OptionValue::getList,
                List::of);

        return this.listsFullReport;
    }

    //#endregion

    private <T> T getOption(Option option, ParseValueFunction<T> parseValueFunc,
        Supplier<T> defaultValueFunc) throws ParserException
    {
        OptionValue optionValue = this.options.get(option);

        if (optionValue != null)
            return parseValueFunc.parse(optionValue);

        return defaultValueFunc.get();
    }

    private <T> T getOption(Option option, Function<OptionValue, T> parseValueFunc,
        Supplier<T> defaultValueFunc)
    {
        OptionValue optionValue = this.options.get(option);

        if (optionValue != null)
            return parseValueFunc.apply(optionValue);

        return defaultValueFunc.get();
    }

    private static Calendar parseDate(String date) throws ParserException
    {
        final SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
        final Calendar cal = Calendar.getInstance();

        try {
            cal.setTime(dateFormat.parse(date));
            return cal;
        } catch (ParseException e) {
            throw new ParserException("Invalid date.", e);
        }
    }

    public static void printHelp() {
        System.out.println("-> report <web | mail> [options] <report-file-name.xlsx> <list of tests results file names>");
        System.out.println("Create a report of the specified type based on the tests results provided.\n");
        System.out.println(
            "[options]:\n" +

            "\t" + OPTION_WORKING_DIR.getName() + " working-dir-path -> The directory to search the template file. " +
            "If not defined, defaults to the current directory.\n" +

            "\t" + OPTION_TEMPLATE_FILE.getName() + " template-file-path -> The path to the report template. " +
            "If not defined, defaults to \"" + WEB_TEMPLATE_FILE + "\" for a report of type web or " +
            "\"" + MAIL_TEMPLATE_FILE + "\" for a report of type mail in the working directory (working-dir-path).\n" +

            "\t" + OPTION_DATE.getName() +  " -> The date of the report in <" + DATE_FORMAT + "> format. " +
            "If not defined, defaults to the current date.\n" +

            "\t" + OPTION_FULL_REPORT.getName() + " list-name -> The report of the specified list name will have the full results. " +
            "This option can be repeated.\n"
        );
    }

    private static interface ParseValueFunction<T>
    {
        /**
         * Parse a value.
         * 
         * @param value - The value to parse.
         * @return The parsed result.
         * @throws ParserException if an error occurred while parsing the specified value.
         */
        T parse(OptionValue value) throws ParserException;
    }
}