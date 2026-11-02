package com.aquasense.reporting.domain;

public interface SectionVisitor {

    void visit(GroupSection section);

    void visit(ParagraphSection section);

    void visit(BulletListSection section);

    void visit(TableSection section);

    void visit(ChartSection section);
}
