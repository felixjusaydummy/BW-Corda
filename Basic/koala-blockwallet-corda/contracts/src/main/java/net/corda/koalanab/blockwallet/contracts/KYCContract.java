package net.corda.koalanab.blockwallet.contracts;

import net.corda.koalanab.blockwallet.states.KYCState;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;

import static net.corda.core.contracts.ContractsDSL.requireThat;

// ************
// * Contract *
// ************
public class KYCContract implements Contract {
    // This is used to identify our contract when building a transaction.
    public static final String ID = "net.corda.koalanab.blockwallet.contracts.KYCContract";

    // A transaction is valid if the verify() function of the contract of all the transaction's input and output states
    // does not throw an exception.
    @Override
    public void verify(LedgerTransaction tx) {
//
//        final CommandData commandData = tx.getCommands().get(0).getValue();
//
//        if (commandData instanceof Commands.Send) {
//            //Retrieve the output state of the transaction
//            KYCState output = tx.outputsOfType(KYCState.class).get(0);
//
//            //Using Corda DSL function requireThat to replicate conditions-checks
//            requireThat(require -> {
//                require.using("No inputs should be consumed when lastname is empty", !output.getLastname().isEmpty() );
//                require.using("No inputs should be consumed when firstname is empty", !output.getFirstname().isEmpty() );
//                return null;
//            });
//        }
    }

    public interface Commands extends CommandData {
        class Send implements Commands {}
    }
}