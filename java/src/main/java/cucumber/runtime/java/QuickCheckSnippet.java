package cucumber.runtime.java;

class QuickCheckSnippet extends AbstractJavaSnippet {

    @Override
    protected String getArgType(Class<?> argType) {
        return argType.getSimpleName();
    }

    @Override
    public String template() {
        return "@{0}(\"{1}\")\n";
    }
}
