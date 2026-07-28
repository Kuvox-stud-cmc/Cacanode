from __future__ import annotations

from app.generated import cacanode_ai_v1_pb2 as pb


def test_inference_service_preserves_existing_and_appends_interview_methods() -> None:
    methods = [method.name for method in pb.DESCRIPTOR.services_by_name["InferenceService"].methods]
    assert methods == [
        "GenerateAnswer",
        "ListDocumentUnits",
        "DeleteDocumentIndex",
        "PrepareInterviewSession",
        "CancelInterviewSession",
    ]


def test_interview_descriptor_field_numbers_and_optional_evidence() -> None:
    request = pb.PrepareInterviewSessionRequest.DESCRIPTOR
    assert {field.name: field.number for field in request.fields} == {
        "session_id": 1,
        "call_attempt_id": 2,
        "tenant_id": 3,
        "template_revision_id": 4,
        "snapshot_version": 5,
        "snapshot_sha256": 6,
        "company_display_name": 7,
        "candidate_display_name": 8,
        "introduction_text": 9,
        "disclosure_text": 10,
        "closing_text": 11,
        "duration_limit_seconds": 12,
        "interaction_limits": 13,
        "recording_enabled": 14,
        "cv_personalization_enabled": 15,
        "sections": 16,
        "trace": 17,
    }
    evidence = pb.InterviewQuestionSnapshot.DESCRIPTOR.fields_by_name["evidence"]
    assert evidence.has_presence is True
    assert pb.INTERVIEW_SECTION_KIND_CORE == 1
    assert pb.INTERVIEW_QUESTION_SOURCE_CV_PERSONALIZED == 2
