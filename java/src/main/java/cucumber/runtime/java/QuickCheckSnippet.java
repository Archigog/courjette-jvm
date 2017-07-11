package cucumber.runtime.java;

import java.util.List;

class QuickCheckSnippet extends AbstractJavaSnippet {

    @Override
    protected String getArgType(Class<?> argType) {
        return argType.getSimpleName();
    }

    @Override
    public String arguments(List<Class<?>> argumentTypes) {
        StringBuilder sb = new StringBuilder();
        int prevArgType = 0;
        int n = 1;
        for (Class<?> argType : argumentTypes) {
            if (n > 1 && prevArgType == 0) {
                sb.append(", ");
            }

            sb.append("@From(")
                    .append(getArgType(argType))
                    .append("Generator.class)")
                    .append(" ")
                    .append(getArgType(argType));
            if (isGenericType(argType)) {
                prevArgType += 1;
                sb.append("<");
                continue;
            }
            while (prevArgType > 0) {
                prevArgType -= 1;
                sb.append(">");
            }
            sb.append(" ")
                    .append("arg")
                    .append(n++);
        }
        return sb.toString();
    }

    private boolean isGenericType(Class<?> argType) {
        Boolean result = Boolean.FALSE;
        if (getArgType(argType).equals("ArrayList") ||
                getArgType(argType).equals("LinkedList") ||
                getArgType(argType).equals("HashSet") ||
                getArgType(argType).equals("LinkedHashSet") ||
                // TODO : if HashMap, different because <K, V>
                getArgType(argType).equals("HashMap") ||
                getArgType(argType).equals("LinkedHashMap")) {
            result = Boolean.TRUE;
        }
        return result;
    }

    @Override
    public String template() {
        return "@{0}(\"{1}\")\n" +
                "@Property\n" +
                "public void {2}({3}) throws Throwable '{'\n" +
                "    // {4}\n" +
                "{5}    throw new PendingException();\n" +
                "'}'\n";

        /*
        @Given("^any integer$")
        @Property
        public void any_integer(@From(IntegerGenerator.class) Integer integer) {
            // Write code here that turns the phrase above into concrete actions
            throw new PendingException();
        }
        */
    }
}
