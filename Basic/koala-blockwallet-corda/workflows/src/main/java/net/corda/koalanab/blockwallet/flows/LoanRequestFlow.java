package net.corda.koalanab.blockwallet.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.corda.koalanab.blockwallet.contracts.LoanContract;
import net.corda.koalanab.blockwallet.states.LoanState;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;

import java.time.ZoneId;
import java.util.Date;

import static net.corda.core.contracts.ContractsDSL.requireThat;

/**
 * This flow allows two parties (the [Initiator] and the [Acceptor]) to come to an agreement about the IOU encapsulated
 * within an [IOUState].
 *
 * In our simple example, the [Acceptor] always accepts a valid IOU.
 *
 * These flows have deliberately been implemented by using only the call() method for ease of understanding. In
 * practice we would recommend splitting up the various stages of the flow into sub-routines.
 *
 * All methods called within the [FlowLogic] sub-class need to be annotated with the @Suspendable annotation.
 */
public class LoanRequestFlow {
    @InitiatingFlow
    @StartableByRPC
    @StartableByService
    public static class LoanRequestInitiator extends FlowLogic<SignedTransaction> {

        private final Step GENERATING_INIT = new Step("Init transaction based on new IOU.");
        private final Step GENERATING_TRANSACTION = new Step("Generating transaction based on new IOU.");
        private final Step VERIFYING_TRANSACTION = new Step("Verifying contract constraints.");
        private final Step SIGNING_TRANSACTION = new Step("Signing transaction with our private key.");
        private final Step GATHERING_SIGS = new Step("Gathering the counterparty's signature.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return CollectSignaturesFlow.Companion.tracker();
            }
        };
        private final Step FINALISING_TRANSACTION = new Step("Obtaining notary signature and recording transaction.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return FinalityFlow.Companion.tracker();
            }
        };

        // The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
        // checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call()
        // function.
        private final ProgressTracker progressTracker = new ProgressTracker(
                GENERATING_INIT,
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
        );

        private final long walletAccountId;
        private final String purpose;
        private final String amount;
        private final String paymentTerms;
        private final String occupation;
        private final String grossIncome;
        private final UniqueIdentifier kycId;
        // other party is the affiliate banks
        private final Party otherParty;

        public LoanRequestInitiator(
            long walletAccountId, String purpose, String amount, String paymentTerms, String occupation, String grossIncome,
            UniqueIdentifier kycId,
            Party otherParty
        ){
            this.walletAccountId = walletAccountId;
            this.purpose = purpose;
            this.amount = amount;
            this.paymentTerms = paymentTerms;
            this.occupation = occupation;
            this.grossIncome = grossIncome;
            this.kycId = kycId;
            this.otherParty = otherParty;
        }

        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // Obtain a reference to the notary we want to use.
//            final Step GENERATING_INITxx = new Step("Init transaction based on new IOU: "+ this.purpose);
            progressTracker.setCurrentStep(GENERATING_INIT);
            System.out.println("[Corda Steps] "+ this.purpose);
            getLogger().info("Testing corda 1111...");


            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
            final LoanState currentState = new LoanState(
                    new UniqueIdentifier(),
                    walletAccountId,
                    purpose,
                    amount,
                    paymentTerms,
                    occupation,
                    grossIncome,
                    kycId,
                    false,
                    null,
                    (new Date()).toString(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    getOurIdentity(),
                    otherParty
            );

            // Stage 1.
            progressTracker.setCurrentStep(GENERATING_TRANSACTION);
            final Command<LoanContract.Commands.Request> txCommand = new Command<>(
                    new LoanContract.Commands.Request(),
                    ImmutableList.of(currentState.getSender().getOwningKey(), currentState.getReceiver().getOwningKey()));

            final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                    .addOutputState(currentState, LoanContract.ID)
//                    .addOutputState(currentState)
                    .addCommand(txCommand);

            // Stage 2.
            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            // Verify that the transaction is valid.
            txBuilder.verify(getServiceHub());

            // Stage 3.
            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            // Sign the transaction.
            final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder);

            // Stage 4.
            progressTracker.setCurrentStep(GATHERING_SIGS);
            // Send the state to the counterparty, and receive it back with their signature.
            FlowSession otherPartySession = initiateFlow(currentState.getReceiver());
            final SignedTransaction fullySignedTx = subFlow(
                    new CollectSignaturesFlow(partSignedTx, ImmutableSet.of(otherPartySession), CollectSignaturesFlow.Companion.tracker()));

            // Stage 5.
            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            // Notarise and record the transaction in both parties' vaults.
            return subFlow(new FinalityFlow(fullySignedTx, ImmutableSet.of(otherPartySession)));
        }
    }

    @InitiatedBy(LoanRequestInitiator.class)
    public static class Acceptor extends FlowLogic<SignedTransaction> {

        private final FlowSession otherPartySession;

        public Acceptor(FlowSession otherPartySession) {
            this.otherPartySession = otherPartySession;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            class SignTxFlow extends SignTransactionFlow {
                private SignTxFlow(FlowSession otherPartyFlow, ProgressTracker progressTracker) {
                    super(otherPartyFlow, progressTracker);
                }

                @Override
                protected void checkTransaction(SignedTransaction stx) {
                    requireThat(require -> {
                        ContractState output = stx.getTx().getOutputs().get(0).getData();
                        require.using("This must be an IOU transaction.", output instanceof LoanState);
                        return null;
                    });
                }
            }
            final SignTxFlow signTxFlow = new SignTxFlow(otherPartySession, SignTransactionFlow.Companion.tracker());
            final SecureHash txId = subFlow(signTxFlow).getId();

            return subFlow(new ReceiveFinalityFlow(otherPartySession, txId));
        }
    }
}
