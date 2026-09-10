package ee.example.itagent.api.dto;

public enum RefusalReason {
    OUT_OF_SCOPE("Küsimus ei puuduta IT teenuseid, mille kohta ma vastata saan."),
    SENSITIVE_REQUEST("Ma ei saa vastata päringutele, mis nõuavad tundliku teabe avaldamist."),
    SENSITIVE_DATA_IN_INPUT("Küsimus sisaldas tundlikke andmeid (nt parool või ligipääsuvõti). "
            + "Palun ära jaga siin selliseid andmeid."),
    NO_SOURCE_MATCH("Teadmusbaasist ei leitud sellele küsimusele kinnitatud vastust."),
    INJECTION_SUSPECTED("Ma ei saa seda päringut täita."),
    LOW_CONFIDENCE("Ma ei leia teadmusbaasist selle küsimuse kohta piisavalt kindlat vastust."),
    CLARIFICATION_NEEDED("Küsimus on liiga üldine, palun täpsusta oma küsimust.");

    private final String message;

    RefusalReason(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
