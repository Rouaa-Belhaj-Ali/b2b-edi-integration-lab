# EDI Flow Simulator

A small, **fully fictional** Java simulation of a B2B/EDI integration: an inbound EDIFACT **ORDRSP**
(purchase order response) arrives from a trading partner, is recognized and stored, mapped into an internal
document, converted to a fixed-width flat file, and delivered to a partner over a file protocol.
The partner picks it up and acknowledges it.

It exists to practise the part of integration work that is rarely taught: **finding out why it broke**.
Nine scenarios reproduce realistic failures, each with the call chain, the pipeline at the failing step,
and a one-line lesson.

> Everything here is made up: partners, IDs, message content, the flat-file layout. No real company,
> server, partner specification or vendor code is included. It is a learning model, not a copy of any product.

## What it models

| Real-world piece | In this project |
|---|---|
| Trading-network layer (recognize, store, route, status, log) | `TN`, `BizDoc` |
| Flow services and the service namespace | `Services`, `Registry`, `Flow` |
| The pipeline (variables passed between steps) | `Pipeline` |
| The entry-point flow: branch, Try, Catch | `ProcessOrdrsp` |
| EDI parsing and validation | `Edifact` |
| Mapping EDIFACT to an internal document, then to a flat file | `Services.inboundMap`, `Services.generateSnf` |
| Debug shortcut: save/restore a pipeline | `PipelineStore` |
| Partner delivery over a file protocol, and the partner's ack | `FileTransport`, `PartnerSim` |
| Checking a copied package for foreign references | `DependencyCheck` |

The entry-point flow runs these steps, each logged with its nesting:

```
pub.flow:clearPipeline
edi.handler:validateTransaction      -> edi.tn:getEnvDocFromBizdoc -> edi.core:getICDetail
ordrsp.service:get_TP_ID
ordrsp.service:getDeliveryDate
ordrsp.map:inboundMap
ordrsp.service:Generate_SNF
ordrsp.service:validateOutput
ordrsp.delivery:sendFile
ordrsp.tn:updateBizdocStatus
ordrsp.util:logEDIRecord
```

## Run it

**Eclipse:** File > Import > General > Existing Projects into Workspace, pick this folder, then right-click
`Main.java` > Run As > Java Application. (JDK 17 or newer. The working directory must be the project root,
which is the default.)

**Command line:** `./run.sh` (or `run.bat` on Windows). To run only some scenarios: `./run.sh 2 4`.

The run ends with a PASS/FAIL summary and exits non-zero if any scenario does not behave as designed.

## The scenarios

| # | Scenario | What it teaches |
|---|---|---|
| 1 | Happy path, end to end | Know the healthy path first. |
| 2 | The parser receives something that is not raw EDI | The error is raised at the parser; the cause is upstream. Check the input variable of the failing step. |
| 3 | Entry-point service run directly, with no bizdoc | A service built to be triggered has no input window. Replay a real document. |
| 4 | Debug shortcut restores another developer's pipeline | Shared debug state can hold someone else's data. Key it per person. |
| 5 | Copied package still calls the original package | Runs fine, ships the wrong partner ID. Scan references after a copy. |
| 6 | Locked value left in a copied map | Wrong output with no error. Validate output against the lookup. |
| 7 | Unknown trading partner | Fail loudly, mark the document, send nothing. |
| 8 | Wrong segment count from the partner | One wrong number rejects the message. Compare with the raw file. |
| 9 | File delivery: retry, then give up cleanly | Temporary name plus rename, bounded retries, no partial files. |

### Example: scenario 2

```
RESULT: FAILED -> Unrecognized input data type [SIM-EDI.000010.000032] (got LinkedHashMap)
call chain (innermost first): edi.core:getICDetail <- edi.handler:validateTransaction <- ordrsp.handler:processORDRSP
pipeline as the failing step saw it:
    bizdoc         BizDoc       BD-0001 (ORDRSP, SELLERX, PROCESSING)
    edidata        LinkedHashMap {note=already-converted document, not raw EDI}
    user           String       dev1
```

The message names a symptom at the parser. The pipeline shows the real problem: `edidata` is not what the
parser expects.

## Case study and further reading

- [`docs/INCIDENT_NOTE.md`](docs/INCIDENT_NOTE.md): an anonymized incident note for the kind of failure scenarios 2 to 4 model,
  with ranked hypotheses and how to test each one.
- [`docs/BLOG.md`](docs/BLOG.md): how an integration developer thinks, from first look to root cause.

## Limits

- The EDIFACT reader handles the segments used here and ignores release characters. It is not a full parser.
- The flat-file layout is illustrative.
- File transport is simulated on the local disk under `sim-data/` (ignored by git).
- Saved pipelines live in memory. On a real server they are typically files on disk, which is what makes them easy to share by accident.

Product names mentioned in comments or docs belong to their owners; this project is not affiliated with any vendor.
