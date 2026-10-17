import pytest

from evals.seed import SeedError, assert_local
from evals.verify_oracle import compare


def test_seeding_is_refused_for_anything_that_is_not_a_local_server():
    for url in ("http://localhost:8080/api/v1", "http://127.0.0.1:8081/api/v1", "http://[::1]:8080/api/v1"):
        assert_local(url)  # does not raise

    for url in (
        "https://alphatrackerv2-production.up.railway.app/api/v1",   # the real deployment
        "http://localhost.evil.com/api/v1",                           # 'localhost' as a prefix, not the host
        "http://localhost@evil.com/api/v1",                           # 'localhost' as the userinfo, host is evil.com
        "http://192.168.1.20:8080/api/v1",                            # another machine on the network
        "not a url",
    ):
        with pytest.raises(SeedError):
            assert_local(url)


def test_comparison_catches_real_differences_and_treats_no_data_strictly():
    def problems_for(got, want):
        found: list[str] = []
        compare("x", got, want, found)
        return found

    assert problems_for({"a": 1.004, "b": [1, 2], "extra": 9}, {"a": 1.0, "b": [1, 2]}) == []   # cents tolerance; extras ignored

    assert len(problems_for({"a": 1.02}, {"a": 1.0})) == 1                 # off by two cents
    assert len(problems_for({"b": [1, 3]}, {"b": [1, 2]})) == 1            # a list element differs
    assert len(problems_for({"b": [1]}, {"b": [1, 2]})) == 1               # a list is the wrong length
    assert len(problems_for({}, {"a": 1})) == 1                            # a field is missing
    assert len(problems_for({"rate": 0.0}, {"rate": None})) == 1           # 0 is NOT "no data"
    assert len(problems_for({"rate": None}, {"rate": 0.0})) == 1           # ...and no data is NOT 0
    assert problems_for({"rate": None}, {"rate": None}) == []
