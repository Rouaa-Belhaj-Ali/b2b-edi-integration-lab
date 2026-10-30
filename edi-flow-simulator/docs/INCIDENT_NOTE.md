# Incident note (anonymized): "Unrecognized input data type" in a copied inbound ORDRSP flow

**Status:** root cause not confirmed. The investigation was interrupted, so the hypotheses below are ranked, not proven.

## Summary

A developer copied an inbound EDIFACT ORDRSP integration (EDI in, flat file out to a partner over a file protocol)
into a new package and tried to debug it from the IDE. The entry-point service failed with
`Unrecognized input data type`, no input dialog appeared, and the pipeline panel was empty.
About a week had already been lost because the original package was not visible on the server connection
until the view was refreshed; during that time schemas were rebuilt by hand.

## What was observed

- The entry-point service was started directly from the IDE, not by the trigger.
- Tracing the execution showed this chain: trigger, entry-point flow, `validateTransaction`,
  `getEnvDocFromBizdoc`, `getICDetail`. The failure was raised in the last one.
- The trading-network document (bizdoc) was retrieved successfully. The failure happened while processing its EDI data.
- Disabling the pipeline-restore branch did not change the error.
- While debugging a shared, public service in the IDE, the restored data was not EDI. A senior colleague explained that
  the latest incoming data of a shared service may belong to someone else.

## Hypotheses, ranked

| # | Hypothesis | How to test | Model |
|---|---|---|---|
| 1 | The content reaching the parse step is not raw EDI (wrong type, already-converted, wrong content part). | At the input of the parse step, record the variable name, its type, and the first 20 characters. | Scenario 2 |
| 2 | The bizdoc used for debugging is not an ORDRSP interchange (wrong document type or processing rule). | Open the bizdoc: document type, sender, receiver, content part names. | Scenario 2 |
| 3 | The copied package still references objects, data or configuration of the original. | Scan the flows for foreign references; compare lookups and schemas with the original. | Scenarios 5, 6 |
| 4 | Debug data restored from a shared "last run" belonged to someone else. | Check what was restored; check whether the restore branch actually executed. | Scenario 4 |
| 5 | Environment mix-up (test vs. production, a different instance, a shared database). | Confirm host, port and instance; check which connection aliases the package uses. | Not modelled |

**Why hypothesis 4 is probably not the main cause for the entry-point failure:** the restore branch only runs when the
trace flag is set. With the flag empty, the other branch runs and the restore is never executed, so switching it off
changes nothing. A test only counts if the thing you switched off was really in the execution path.

## Contributing factors (process)

- The package was not visible for about a week; the connection view was not refreshed and nobody was asked early.
- A service designed to be triggered by the trading-network layer was run bare from the IDE.
- Unclear which server instance was in use, and whether it was the one used by the people helping.

## Next steps

1. Run the failing step with a real, known-good ORDRSP document replayed through the trigger.
2. At the input of the parse step, capture name, type and the start of the content.
3. Confirm the bizdoc's document type and content parts.
4. Scan the copied package for foreign references.
5. Confirm the server instance in use.

## Lessons

- The error message names a symptom at one step; the cause is usually upstream. Look at the **input** of the failing step first.
- Rank hypotheses and test the cheapest one that could change your mind.
- Ask early, with specifics: which flow, which step, what the input variable contained.
- After copying a package, check references before debugging behaviour.
