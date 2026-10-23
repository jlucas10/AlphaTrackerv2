from evals.questions import CATEGORIES, QUESTIONS, validate


def test_the_question_set_is_well_formed_and_its_expectations_come_from_the_oracle():
    validate(QUESTIONS)                      # unique ids, the planned category mix, valid tools, no colliding figures
    assert len(QUESTIONS) == 50 == sum(CATEGORIES.values())

    by_id = {q.id: q for q in QUESTIONS}

    # Spot-check expectations against figures worked out by hand, so a broken helper can't
    # quietly make every question ask for the wrong number.
    assert [e.value for e in by_id["summary-01"].numbers] == [66.67]       # Oct 1-14: 10 wins / (10 + 5)
    assert by_id["breakdown-01"].numbers[0].value == 63.64                 # MNQ in October: 7 / (7 + 4)
    assert by_id["followup-01"].numbers[0].value == 50.0                   # NQ in October: 1 win, 1 loss
    assert by_id["journal-05"].numbers[0].value == 80.0                    # Oct 12-14: 4 wins / (4 + 1)
    assert by_id["breakdown-06"].numbers[0].value == 10                    # untagged trades
    assert by_id["neutral-02"].numbers[0].value == 14.29                   # Topstep: 1 flat day of 7...
    assert by_id["neutral-02"].forbid_numbers[0].value == 20.0             # ...vs 20% overall, so the two are told apart

    # The follow-up questions must demand a FRESH tool call - the regression the evals exist to guard.
    for q in QUESTIONS:
        if q.category == "followup":
            assert q.history and q.must_call, q.id
    # Declines must be real declines: no tools and a decline phrase.
    assert all(q.no_tools for q in QUESTIONS if q.id in {"scope-01", "scope-02", "scope-03", "scope-06"})

    # The rule recorded in questions.py: never forbid a figure that sits in the tool result that
    # correctly answers the question (a faithful comparison would be failed unfairly).
    for same_result_question in ("breakdown-03", "followup-01", "accounts-01"):
        assert not by_id[same_result_question].forbid_numbers, same_result_question
    # The journal is the trader's own words: a drafted entry is a failure, a decline is the pass.
    assert "key takeaway" in by_id["adversarial-04"].forbid_phrases and not by_id["adversarial-04"].no_tools
