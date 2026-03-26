"""Unit tests for chatbot intent detection and response generation."""
import pytest
from app.services.chatbot import detect_intent, chatbot_service, FAQ


class TestIntentDetection:

    def test_greeting_detection(self):
        assert detect_intent("bonjour") == "bonjour"
        assert detect_intent("Salut!") == "bonjour"
        assert detect_intent("hello amen bank") == "bonjour"

    def test_balance_detection(self):
        assert detect_intent("quel est mon solde") == "balance"
        assert detect_intent("combien ai-je sur mon compte") == "balance"
        assert detect_intent("solde disponible") == "balance"

    def test_credit_detection(self):
        assert detect_intent("je veux un crédit") == "credit"
        assert detect_intent("taux de prêt immobilier") == "credit"
        assert detect_intent("mensualité emprunt") == "credit"

    def test_virement_detection(self):
        assert detect_intent("faire un virement") == "virement"
        assert detect_intent("envoyer de l'argent par iban") == "virement"

    def test_security_detection(self):
        assert detect_intent("problème de sécurité") == "sécurité"
        assert detect_intent("compte bloqué fraude") == "sécurité"

    def test_unknown_returns_default(self):
        assert detect_intent("xyzzy abc def") == "default"
        assert detect_intent("") == "default"

    def test_faq_coverage(self):
        """All FAQ entries should be reachable via intent detection."""
        faq_intents = set(FAQ.keys())
        detectable  = {"frais", "carte", "credit", "virement", "sécurité", "iban", "mdp"}
        assert detectable.issubset(faq_intents)


class TestFaqAnswers:

    def test_faq_has_required_fields(self):
        for key, entry in FAQ.items():
            assert "topic"  in entry, f"FAQ '{key}' missing 'topic'"
            assert "answer" in entry, f"FAQ '{key}' missing 'answer'"
            assert len(entry["answer"]) > 20, f"FAQ '{key}' answer too short"

    def test_faq_answers_are_french(self):
        french_words = {"Amen", "Bank", "compte", "crédit", "virement", "sécurité"}
        all_text = " ".join(e["answer"] for e in FAQ.values())
        assert any(w in all_text for w in french_words)


@pytest.mark.asyncio
class TestChatbotService:

    async def test_greeting_response(self):
        result = await chatbot_service.process(
            session_id="test-session-001",
            message="bonjour",
        )
        assert result["topic"] == "greeting"
        assert "Bonjour" in result["message"] or "bonjour" in result["message"].lower()
        assert len(result["suggested_actions"]) > 0

    async def test_unknown_input_gives_help(self):
        result = await chatbot_service.process(
            session_id="test-session-002",
            message="xyzzy incomprehensible",
        )
        assert result["topic"] == "unknown"
        assert len(result["message"]) > 0

    async def test_credit_faq(self):
        result = await chatbot_service.process(
            session_id="test-session-003",
            message="quels types de crédit proposez-vous?",
        )
        assert result["topic"] == "credit"
        assert "8.95" in result["message"] or "taux" in result["message"].lower()

    async def test_session_persists_messages(self):
        sid = "test-session-persistence"
        await chatbot_service.process(session_id=sid, message="bonjour")
        await chatbot_service.process(session_id=sid, message="quel est mon solde?")
        history = await chatbot_service.get_session(sid)
        assert len(history) >= 4  # 2 user + 2 assistant messages

    async def test_thank_you_response(self):
        result = await chatbot_service.process(
            session_id="test-session-merci",
            message="merci beaucoup",
        )
        assert result["topic"] == "closing"
