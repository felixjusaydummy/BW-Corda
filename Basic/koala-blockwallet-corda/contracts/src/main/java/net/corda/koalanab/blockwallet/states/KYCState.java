package net.corda.koalanab.blockwallet.states;

import net.corda.koalanab.blockwallet.contracts.KYCContract;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Getter
@AllArgsConstructor
@BelongsToContract(KYCContract.class)
public class KYCState implements ContractState {

    private final UniqueIdentifier linearId;
    private final long accountId;
    private final String lastname;
    private final String firstname;
    private final String middlename;
    private final String birthday;
    private final String permanentAddress;
    private final String currentAddress;
    private final String fathername;
    private final String mothername;
    private final String gender;
    private final String contactNo;
    private final String maritalStatus;
    private final String nationality;
    private final String occupation;
    private final String income;

    private final Party sender;
    private final Party receiver;

    @NotNull
    @Override
    public List<AbstractParty> getParticipants() { return Arrays.asList(sender,receiver); }
}


