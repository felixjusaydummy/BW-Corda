package net.corda.koalanab.blockwallet.contracts;

import net.corda.koalanab.blockwallet.states.LoanState;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;

import static net.corda.core.contracts.ContractsDSL.requireThat;

public class LoanContract implements Contract {
    public static final String ID = "net.corda.koalanab.blockwallet.contracts.LoanContract";

    @Override
    public void verify(LedgerTransaction tx) {

        final CommandData commandData = tx.getCommands().get(0).getValue();

        if (commandData instanceof Commands.Request) {
            LoanState output = tx.outputsOfType(LoanState.class).get(0);

            //Using Corda DSL function requireThat to replicate conditions-checks
            requireThat(require -> {
//                require.using("No inputs should be consumed when amount is empty", output.getAmount()>0);
//                require.using("No inputs should be consumed when maximum load amount is greater than 100,000", output.getAmount()<100000);
                return null;
            });
        } else if (commandData instanceof Commands.Approve) {
            LoanState output = tx.outputsOfType(LoanState.class).get(0);

            //Using Corda DSL function requireThat to replicate conditions-checks
            requireThat(require -> {
                require.using("No inputs should be consumed when remarks empty", !output.getRemarks().isEmpty());
                if (output.isApproved())
                    require.using("No inputs should be consumed when approve date is null", output.getDateApproved() != null);
                else
                    require.using("No inputs should be consumed when approve date is null", output.getDateRejected() != null);
                return null;
            });
        }
    }

    public interface Commands extends CommandData {
        class Request implements Commands {}
        class Approve implements Commands {}
    }
}