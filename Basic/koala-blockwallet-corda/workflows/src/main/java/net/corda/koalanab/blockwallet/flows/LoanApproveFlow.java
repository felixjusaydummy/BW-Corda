package net.corda.koalanab.blockwallet.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.corda.koalanab.blockwallet.contracts.LoanContract;
import net.corda.koalanab.blockwallet.states.LoanState;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.UUID;

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
public class LoanApproveFlow {
    @InitiatingFlow
    @StartableByRPC
    @StartableByService
    public static class LoanApproveInitiator extends FlowLogic<SignedTransaction> {

        private final Step GENERATING_INIT = new Step("Init transaction based on new IOU.");
        private final Step GENERATING_INIT2 = new Step("Init transaction based on new IOU.. 2");
        private final Step GENERATING_INIT3 = new Step("Init transaction based on new IOU... 3");
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
                GENERATING_INIT2,
                GENERATING_INIT3,
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
        );

        private final UUID loanId;;
        private final boolean approve;
        private final String remarks;
        private final String creditScore;

        public LoanApproveInitiator(
                UUID loanId, boolean approve, String remarks, String creditScore
        ){
            this.loanId = loanId;
            this.approve = approve;
            this.remarks = remarks;
            this.creditScore = creditScore;
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
            List<StateAndRef<LoanState>> auctionStateAndRefs = getServiceHub().getVaultService()
                    .queryBy(LoanState.class).getStates();

            progressTracker.setCurrentStep(GENERATING_INIT2);
            StateAndRef<LoanState> inputStateAndRef = auctionStateAndRefs.stream().filter(auctionStateAndRef -> {
                LoanState loanState = auctionStateAndRef.getState().getData();
                return loanState.getLinearId().getId().equals(this.loanId);
            }).findAny().orElseThrow(() -> new IllegalArgumentException("Loan Not Found, wa nakitan"));

            LoanState inputState = inputStateAndRef.getState().getData();

            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
            String dateApprove = (this.approve)? new Date().toString(): null;
            String dateReject = (!this.approve)? new Date().toString(): null;

            progressTracker.setCurrentStep(GENERATING_INIT3);
            // objects
            final LoanState currentState = new LoanState(
                inputState.getLinearId(),
                inputState.getWalletAccountId(),
                inputState.getPurpose(),
                inputState.getAmount(),
                inputState.getPaymentTerms(),
                inputState.getOccupation(),
                inputState.getGrossIncome(),
                inputState.getKycId(),
                this.approve,
                dateApprove,
                inputState.getDateRequested(),
                dateReject,
                this.remarks,
                this.creditScore,
                inputState.getPaidRemarks(),
                inputState.getDatePaid(),
                inputState.getSender(),
                inputState.getReceiver()
            );

            // Stage 1.
            progressTracker.setCurrentStep(GENERATING_TRANSACTION);
            final Command<LoanContract.Commands.Request> txCommand = new Command<>(
                    new LoanContract.Commands.Request(),
                    ImmutableList.of(currentState.getSender().getOwningKey(), currentState.getReceiver().getOwningKey()));

            final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                    .addInputState(inputStateAndRef)
                    .addOutputState(currentState, LoanContract.ID)
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

    @InitiatedBy(LoanApproveInitiator.class)
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
