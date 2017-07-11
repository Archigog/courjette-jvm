package cucumber.runtime.snippets;

import com.sun.org.apache.xpath.internal.operations.Bool;
import cucumber.api.DataTable;
import gherkin.I18n;
import gherkin.formatter.model.Step;

import java.math.BigDecimal;
import java.text.MessageFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SnippetGenerator {
    private static final ArgumentPattern[] DEFAULT_ARGUMENT_PATTERNS = new ArgumentPattern[]{
            new ArgumentPattern(Pattern.compile("\"([^\"]*)\""), String.class),
            new ArgumentPattern(Pattern.compile("(\\d+)"), Integer.TYPE),
            new ArgumentPattern(Pattern.compile("any"), String.class),
            new ArgumentPattern(Pattern.compile("any \"([^\"]*)\" from \"([^\"]*)\""), String.class),
            new ArgumentPattern(Pattern.compile("any \"([^\"]*)\" to \"([^\"]*)\""), String.class)
    };
    private static final ArgumentPattern[] QUICKCHECK_GENERATOR_PATTERNS = new ArgumentPattern[]{
            new ArgumentPattern(Pattern.compile("(?i)byte(s)?"), Byte.class),
            new ArgumentPattern(Pattern.compile("(?i)short(s)?"), Short.class),
            new ArgumentPattern(Pattern.compile("(?i)int(eger)?(s)?"), Integer.class),
            new ArgumentPattern(Pattern.compile("(?i)long(s)?"), Long.class),
            new ArgumentPattern(Pattern.compile("(?i)float(s)?"), Float.class),
            new ArgumentPattern(Pattern.compile("(?i)double(s)?"), Double.class),
            new ArgumentPattern(Pattern.compile("(?i)boolean(s)?"), Boolean.class),
            new ArgumentPattern(Pattern.compile("(?i)char(acter)?(s)?"), Character.class),
            new ArgumentPattern(Pattern.compile("(?i)big( )?decimal(s)?"), BigDecimal.class),
            new ArgumentPattern(Pattern.compile("(?i)big( )?integer(s)?"), BigDecimal.class),
            new ArgumentPattern(Pattern.compile("(?i)date(s)?"), Date.class),
            new ArgumentPattern(Pattern.compile("(?i)enum(eration)?(s)?"), Enum.class),
            new ArgumentPattern(Pattern.compile("(?i)string(s)?"), String.class),
            new ArgumentPattern(Pattern.compile("(?i)(array(s)?|list(s)?|array( )?list(s)?)"), ArrayList.class),
            new ArgumentPattern(Pattern.compile("(?i)linked( )?list(s)?"), LinkedList.class),
            new ArgumentPattern(Pattern.compile("(?i)hash( )?set(s)?"), HashSet.class),
            new ArgumentPattern(Pattern.compile("(?i)linked( )?hash( )?set(s)?"), LinkedHashSet.class),
            new ArgumentPattern(Pattern.compile("(?i)hash( )?map(s)?"), HashMap.class),
            new ArgumentPattern(Pattern.compile("(?i)linked( )?hash( )?map(s)?"), LinkedHashMap.class)
    };
    private static final Pattern GROUP_PATTERN = Pattern.compile("\\(");

    private static final Pattern[] ESCAPE_PATTERNS = new Pattern[]{
            Pattern.compile("\\$"),
            Pattern.compile("\\("),
            Pattern.compile("\\)"),
            Pattern.compile("\\["),
            Pattern.compile("\\]"),
            Pattern.compile("\\?"),
            Pattern.compile("\\*"),
            Pattern.compile("\\+"),
            Pattern.compile("\\."),
            Pattern.compile("\\^")
    };
    private static final String REGEXP_HINT = "Write code here that turns the phrase above into concrete actions";

    private final Snippet snippet;
    private final Snippet quickCheckSnippet;

    public SnippetGenerator(Snippet snippet) {
        this.snippet = snippet;
        this.quickCheckSnippet = null;
    }

    public SnippetGenerator(Snippet snippet, Snippet quickCheckSnippet) {
        this.snippet = snippet;
        this.quickCheckSnippet = quickCheckSnippet;
    }

    public String getSnippet(Step step, FunctionNameGenerator functionNameGenerator) {
        // TODO : change quickCheckSnippet for quickCheck : define TRUE before this line
        Snippet currentSnippet = isQuickCheckStep(step) ? quickCheckSnippet : snippet;
        return MessageFormat.format(
                    currentSnippet.template(),
                    I18n.codeKeywordFor(step.getKeyword()),
                    currentSnippet.escapePattern(patternFor(step.getName())),
                    functionName(step.getName(), functionNameGenerator),
                    currentSnippet.arguments(argumentTypes(step)),
                    REGEXP_HINT,
                    step.getRows() == null ? "" : currentSnippet.tableHint()
            );
    }

    private boolean isQuickCheckStep(Step step) {
        Boolean result = Boolean.FALSE;
        String name = step.getName();
        Matcher[] matchers = new Matcher[argumentPatterns().length];
        for (int i = 0; i < argumentPatterns().length; i++) {
            matchers[i] = argumentPatterns()[i].pattern().matcher(name);
        }
        int pos = 0;
        while (true) {
            int matchedLength = 1;

            for (Matcher matcher : matchers) {
                Matcher m = matcher.region(pos, name.length());
                if (m.lookingAt()) {
                    // If we are in a basic QuickCheck situation
                    if (m.group().equals("any")) {
                        result = Boolean.TRUE;
                    }
                    matchedLength = m.group().length();
                    break;
                }
            }

            pos += matchedLength;

            if (pos == name.length()) {
                break;
            }
        }

        return result;
    }

    String patternFor(String stepName) {
        String pattern = stepName;
        for (Pattern escapePattern : ESCAPE_PATTERNS) {
            Matcher m = escapePattern.matcher(pattern);
            String replacement = Matcher.quoteReplacement(escapePattern.toString());
            pattern = m.replaceAll(replacement);
        }
        for (ArgumentPattern argumentPattern : argumentPatterns()) {
            pattern = argumentPattern.replaceMatchesWithGroups(pattern);
        }
        if (snippet.namedGroupStart() != null) {
            pattern = withNamedGroups(pattern);
        }

        return "^" + pattern + "$";
    }

    private String functionName(String sentence, FunctionNameGenerator functionNameGenerator) {
        if(functionNameGenerator == null) {
            return null;
        }
        for (ArgumentPattern argumentPattern : argumentPatterns()) {
            sentence = argumentPattern.replaceMatchesWithSpace(sentence);
        }
        return functionNameGenerator.generateFunctionName(sentence);
    }


    private String withNamedGroups(String snippetPattern) {
        Matcher m = GROUP_PATTERN.matcher(snippetPattern);

        StringBuffer sb = new StringBuffer();
        int n = 1;
        while (m.find()) {
            m.appendReplacement(sb, "(" + snippet.namedGroupStart() + n++ + snippet.namedGroupEnd());
        }
        m.appendTail(sb);

        return sb.toString();
    }


    private List<Class<?>> argumentTypes(Step step) {
        String name = step.getName();
        List<Class<?>> argTypes = new ArrayList<Class<?>>();
        Matcher[] matchers = new Matcher[argumentPatterns().length];
        for (int i = 0; i < argumentPatterns().length; i++) {
            matchers[i] = argumentPatterns()[i].pattern().matcher(name);
        }
        int pos = 0;
        while (true) {
            int matchedLength = 1;

            for (int i = 0; i < matchers.length; i++) {
                Matcher m = matchers[i].region(pos, name.length());
                if (m.lookingAt()) {
                    // If we are in a basic QuickCheck situation, use the QuickCheck arguments function
                    if (m.group().equals("any")) {
                        argTypes.addAll(quickCheckGeneratorArguments(name.substring(m.end())));
                        break;
                    // If not, add the argument to the list
                    } else {
                        Class<?> typeForSignature = argumentPatterns()[i].type();
                        argTypes.add(typeForSignature);
                    }

                    matchedLength = m.group().length();
                    break;
                }
            }

            pos += matchedLength;

            if (pos == name.length()) {
                break;
            }
        }
        if (step.getDocString() != null) {
            argTypes.add(String.class);
        }
        if (step.getRows() != null) {
            argTypes.add(DataTable.class);
        }
        return argTypes;
    }

    private List<Class<?>> quickCheckGeneratorArguments(String substep) {
        List<Class<?>> argTypes = new ArrayList<Class<?>>();
        Matcher[] matchers = new Matcher[quickCheckGeneratorPatterns().length];
        for (int i = 0; i < quickCheckGeneratorPatterns().length; i++) {
            matchers[i] = quickCheckGeneratorPatterns()[i].pattern().matcher(substep);
        }
        int pos = 0;
        while (true) {
            int matchedLength = 1;

            for (int i = 0; i < matchers.length; i++) {
                Matcher m = matchers[i].region(pos, substep.length());
                if (m.lookingAt()) {
                    Class<?> typeForSignature = quickCheckGeneratorPatterns()[i].type();
                    argTypes.add(typeForSignature);

                    matchedLength = m.group().length();
                    break;
                }
            }

            pos += matchedLength;

            if (pos == substep.length()) {
                break;
            }
        }

        return argTypes;
    }

    ArgumentPattern[] argumentPatterns() {
        return DEFAULT_ARGUMENT_PATTERNS;
    }

    ArgumentPattern[] quickCheckGeneratorPatterns() {
        return QUICKCHECK_GENERATOR_PATTERNS;
    }

    public static String untypedArguments(List<Class<?>> argumentTypes) {
        StringBuilder sb = new StringBuilder();
        for (int n = 0; n < argumentTypes.size(); n++) {
            if (n > 0) {
                sb.append(", ");
            }
            sb.append("arg").append(n + 1);
        }
        return sb.toString();
    }
}
