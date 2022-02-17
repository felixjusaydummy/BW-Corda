package net.corda.koalanab.blockwallet.contracts;

import net.corda.koalanab.blockwallet.states.CashInState;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.identity.AbstractParty;
import net.corda.core.transactions.LedgerTransaction;

import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireThat;
import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;

// ************
// * Contract *
// ************
public class CashInContract implements Contract {
    // This is used to identify our contract when building a transaction.
    public static final String ID = "net.corda.koalanab.blockwallet.contracts.CashInContract";

    // A transaction is valid if the verify() function of the contract of all the transaction's input and output states
    // does not throw an exception.
    @Override
    public void verify(LedgerTransaction tx) {

        /* We can use the requireSingleCommand function to extract command data from transaction.
         * However, it is possible to have multiple commands in a signle transaction.*/
        //final CommandWithParties<Commands> command = requireSingleCommand(tx.getCommands(), Commands.class);
        final CommandData commandData = tx.getCommands().get(0).getValue();

        if (commandData instanceof Commands.Send) {
            //Retrieve the output state of the transaction
            CashInState output = tx.outputsOfType(CashInState.class).get(0);

            final CommandWithParties<Commands.Send> command = requireSingleCommand(tx.getCommands(), Commands.Send.class);

            //Using Corda DSL function requireThat to replicate conditions-checks
            requireThat(require -> {
                require.using("No inputs should be consumed when issuing an IOU.",tx.getInputs().isEmpty());
                require.using("The sender and the receiver cannot be the same entity.",
                        output.getSender() != output.getReceiver());

                require.using("All of the participants must be signers.",
                        command.getSigners().containsAll(output.getParticipants().stream().map(AbstractParty::getOwningKey).collect(Collectors.toList())));

                require.using("No inputs should be consumed when affiliate account is empty", !output.getAffiliateAccount().isEmpty() );
                require.using("No inputs should be consumed when wallet account is empty", !output.getWalletAccount().isEmpty() );
//                require.using("No inputs should be consumed when amount is empty", output.getAmount() > 0 );
                return null;
            });
        }
    }

    // Used to indicate the transaction's intent.
    public interface Commands extends CommandData {
        //In our hello-world app, We will only have one command.
        class Send implements Commands {}
    }
}