# What an integration developer actually does, and how to think when it breaks

People imagine EDI work as mapping fields from one format to another. Mapping is perhaps a third of the job.
The rest is making sure the right document from the right partner arrives, is understood, is transformed correctly,
is delivered, is acknowledged, and that you can prove all of it afterwards.

## The job in one picture

```
partner -> protocol -> receive -> recognize -> store -> process -> map -> output -> deliver -> ack -> audit
```

Every arrow can fail. Most failures are boring: a wrong number, a missing partner profile, a file that
arrived half-written. The skill is finding the boring cause quickly and calmly.

## How to think: follow the data

When something breaks, ask five questions in order:

1. **What came in?** Look at the raw message, not a parsed view of it.
2. **What did this step expect?** Variable name, type, format.
3. **What did it actually get?** The *input* of the failing step, not the error text.
4. **Where is the first place the data is wrong?** Walk upstream from the failure, one step at a time.
5. **What is different from the last time it worked?** Data, configuration, package, environment, person.

## Error messages name symptoms, not causes

A parse step fails with "Unrecognized input data type", three calls below the service you started.
The parser did its job. Something above it handed it the wrong thing: a different type, an already-converted
document, the wrong content part, or someone else's data. Look one level up before changing anything.

Then rank your hypotheses and test the cheapest one first. One rule matters more than the rest:
**an experiment only counts if the thing you changed was in the execution path.** If you disable a branch
that never ran, getting the same error tells you nothing. Check that it actually executed.

## Small details that crash everything

- **Delimiters and service strings.** A different segment terminator and the whole file is "not EDI".
- **Counts and control numbers.** One wrong segment count in a trailer rejects the message.
- **Identifiers and qualifiers.** The same partner ID with a different qualifier is a different partner.
- **Copied packages.** A copy that still calls the original's lookups runs perfectly and ships the wrong data.
- **Linked or locked values.** A mapping line copied from someone else can pin a field to a constant.
- **Clearing the pipeline.** Drop what you do not need, but a later step may need it.
- **Shared debug state.** "Restore the last run" may restore someone else's run.
- **Environments.** Test and production sharing a database, or a service alias pointing at the wrong one.
- **File delivery.** Write under a temporary name and rename when complete; retry a limited number of times;
  never leave a partial file where the partner might pick it up.
- **Acknowledgements and timing.** A partner that acknowledges in forty-five minutes breaks anything built for two seconds.
- **Versions.** Which map is actually deployed, not which one was last tested.

## What to be strong at

- **Reading.** Implementation guides, standards, logs, and the raw file, character by character.
- **A debugging method.** Reproduce, narrow, record, then change one thing at a time.
- **Data.** Enough SQL to check lookups and partner profiles yourself.
- **Protocols.** SFTP, AS2, HTTP; retries, idempotency, duplicates.
- **Change control.** Copies, versions, environments, and what depends on what.
- **Communication.** Say what you know, what you do not, and what you tried. Ask early, with specifics:
  which flow, which step, what the input contained. A precise question gets a precise answer.

## One habit that pays

Turn the failure into a small sandbox you can run on your own machine. Write down the call chain,
the input at the failing step, and your ranked hypotheses. When you can reproduce it in a minute and explain
it in two, you are no longer stuck, whatever the real server is doing.

This repository is that habit applied to one flow: nine failures you can run, break and fix.
