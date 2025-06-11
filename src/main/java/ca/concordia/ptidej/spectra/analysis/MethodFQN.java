package ca.concordia.ptidej.spectra.analysis;

// Concrete implementation classes
public class MethodFQN implements IFQN {
    private final String name;

    public MethodFQN(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }
}
