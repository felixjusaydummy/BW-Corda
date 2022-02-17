package net.corda.koalanab.blockwallet.flows;
import co.paralleluniverse.fibers.Suspendable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;


import net.corda.koalanab.blockwallet.contracts.CashInContract;
import net.corda.koalanab.blockwallet.states.CashInState;
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

import static net.corda.core.contracts.ContractsDSL.requireThat;

public class CashInWalletFlow {
    @InitiatingFlow
    @StartableByRPC
    @StartableByService
    public static class CashInWalletInitiator extends FlowLogic<SignedTransaction> {


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

        // note: declaration of state cannot be set in the constructor. it should be placed inside the call function
        private final String affiliateAccount;
        private final String walletAccount;
        private final String amount;
        private final Party otherParty;

        public CashInWalletInitiator(String affiliateAccount, String walletAccount, String amount, Party otherParty) {
            this.affiliateAccount = affiliateAccount;
            this.walletAccount = walletAccount;
            this.amount = amount;
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
            progressTracker.setCurrentStep(GENERATING_INIT);
            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

            final CashInState currentState = new CashInState(
                    new UniqueIdentifier(),
                    affiliateAccount,
                    walletAccount,
                    amount,
                    getOurIdentity(),
                    otherParty
            );

            // Stage 1.
            progressTracker.setCurrentStep(GENERATING_TRANSACTION);
            final Command<CashInContract.Commands.Send> txCommand = new Command<>(
                    new CashInContract.Commands.Send(),
                    ImmutableList.of(currentState.getSender().getOwningKey(), currentState.getReceiver().getOwningKey()));

            final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                    .addOutputState(currentState, CashInContract.ID)
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

    @InitiatedBy(CashInWalletInitiator.class)
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
                        require.using("This must be an IOU transaction.", output instanceof CashInState);
                        CashInState iou = (CashInState) output;
//                        require.using("I won't accept IOUs with a value over 50000.", iou.getAmount() <= 50000);
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
