package pt.unl.fct.di.syspriv.dp;

public class PrivacyBudgetManager {

    private float remainingBudget;

    public PrivacyBudgetManager(float initialBudget) {
        if (!Float.isFinite(initialBudget) || initialBudget <= 0) {
            throw new IllegalArgumentException("Invalid privacy budget");
        }

        this.remainingBudget = initialBudget;
    }

    public synchronized boolean consume(float cost) {
        if (!Float.isFinite(cost) || cost <= 0 || cost > remainingBudget) {
            return false;
        }

        remainingBudget -= cost;
        return true;
    }

    public synchronized float getRemainingBudget() {
        return remainingBudget;
    }
    }