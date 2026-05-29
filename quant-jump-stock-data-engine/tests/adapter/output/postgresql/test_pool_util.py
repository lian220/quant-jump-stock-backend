"""get_validated_conn pre-ping 단위 테스트."""
from unittest.mock import MagicMock
import pytest
from adapter.output.postgresql._pool_util import get_validated_conn


def _mock_pool(connections):
    pool = MagicMock()
    pool.getconn.side_effect = list(connections)
    return pool


def _alive_conn():
    conn = MagicMock()
    cur = conn.cursor.return_value.__enter__.return_value
    cur.execute.return_value = None
    cur.fetchone.return_value = (1,)
    return conn


def _dead_conn():
    conn = MagicMock()
    cur = conn.cursor.return_value.__enter__.return_value
    cur.execute.side_effect = Exception("server closed the connection unexpectedly")
    return conn


def test_alive_connection_returned_directly():
    alive = _alive_conn()
    pool = _mock_pool([alive])
    assert get_validated_conn(pool) is alive
    pool.putconn.assert_not_called()


def test_dead_connection_discarded_and_retry_succeeds():
    dead, alive = _dead_conn(), _alive_conn()
    pool = _mock_pool([dead, alive])
    assert get_validated_conn(pool) is alive
    pool.putconn.assert_called_once_with(dead, close=True)


def test_all_retries_dead_raises_last_exception():
    pool = _mock_pool([_dead_conn(), _dead_conn()])
    with pytest.raises(Exception, match="server closed"):
        get_validated_conn(pool, max_retries=2)
    assert pool.putconn.call_count == 2
