/*
 * This file is generated by jOOQ.
 */
package my.company.jooq.tables.records;


import my.company.jooq.tables.Session;
import org.jetbrains.annotations.NotNull;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record4;
import org.jooq.Row4;
import org.jooq.impl.UpdatableRecordImpl;

import javax.annotation.Generated;
import java.time.Instant;
import java.util.UUID;


/**
 * This class is generated by jOOQ.
 */
@Generated(
        value = {
                "http://www.jooq.org",
                "jOOQ version:3.11.11"
        },
        comments = "This class is generated by jOOQ"
)
@SuppressWarnings({"all", "unchecked", "rawtypes"})
public class SessionRecord extends UpdatableRecordImpl<SessionRecord> implements Record4<UUID, UUID, Instant, Instant> {

    private static final long serialVersionUID = 834398160;

    /**
     * Create a detached SessionRecord
     */
    public SessionRecord() {
        super(Session.SESSION);
    }

    /**
     * Create a detached, initialised SessionRecord
     */
    public SessionRecord(UUID id, UUID userId, Instant createdAt, Instant updatedAt) {
        super(Session.SESSION);

        set(0, id);
        set(1, userId);
        set(2, createdAt);
        set(3, updatedAt);
    }

    /**
     * Getter for <code>public.session.id</code>.
     */
    @NotNull
    public UUID getId() {
        return (UUID) get(0);
    }

    /**
     * Setter for <code>public.session.id</code>.
     */
    public void setId(UUID value) {
        set(0, value);
    }

    /**
     * Getter for <code>public.session.user_id</code>.
     */
    @NotNull
    public UUID getUserId() {
        return (UUID) get(1);
    }

    /**
     * Setter for <code>public.session.user_id</code>.
     */
    public void setUserId(UUID value) {
        set(1, value);
    }

    /**
     * Getter for <code>public.session.created_at</code>.
     */
    @NotNull
    public Instant getCreatedAt() {
        return (Instant) get(2);
    }

    /**
     * Setter for <code>public.session.created_at</code>.
     */
    public void setCreatedAt(Instant value) {
        set(2, value);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    /**
     * Getter for <code>public.session.updated_at</code>.
     */
    public Instant getUpdatedAt() {
        return (Instant) get(3);
    }

    // -------------------------------------------------------------------------
    // Record4 type implementation
    // -------------------------------------------------------------------------

    /**
     * Setter for <code>public.session.updated_at</code>.
     */
    public void setUpdatedAt(Instant value) {
        set(3, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Record1<UUID> key() {
        return (Record1) super.key();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Row4<UUID, UUID, Instant, Instant> fieldsRow() {
        return (Row4) super.fieldsRow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Row4<UUID, UUID, Instant, Instant> valuesRow() {
        return (Row4) super.valuesRow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<UUID> field1() {
        return Session.SESSION.ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<UUID> field2() {
        return Session.SESSION.USER_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<Instant> field3() {
        return Session.SESSION.CREATED_AT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<Instant> field4() {
        return Session.SESSION.UPDATED_AT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UUID component1() {
        return getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UUID component2() {
        return getUserId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Instant component3() {
        return getCreatedAt();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Instant component4() {
        return getUpdatedAt();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UUID value1() {
        return getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UUID value2() {
        return getUserId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Instant value3() {
        return getCreatedAt();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Instant value4() {
        return getUpdatedAt();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SessionRecord value1(UUID value) {
        setId(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SessionRecord value2(UUID value) {
        setUserId(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SessionRecord value3(Instant value) {
        setCreatedAt(value);
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public SessionRecord value4(Instant value) {
        setUpdatedAt(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SessionRecord values(UUID value1, UUID value2, Instant value3, Instant value4) {
        value1(value1);
        value2(value2);
        value3(value3);
        value4(value4);
        return this;
    }
}
