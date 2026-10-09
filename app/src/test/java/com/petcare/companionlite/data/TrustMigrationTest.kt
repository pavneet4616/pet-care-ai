package com.petcare.companionlite.data

import com.petcare.companionlite.data.local.TrustMigration
import java.io.File
import java.sql.DriverManager
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TrustMigrationTest {
    @Test fun upgradePreservesEveryDependentTableWithForeignKeysEnabled() {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { db ->
            val sql=db.createStatement()
            sql.execute("PRAGMA foreign_keys=ON")
            val schema=JSONObject(File("schemas/com.petcare.companionlite.data.local.PetCareDatabase/5.json").readText()).getJSONObject("database").getJSONArray("entities")
            for(i in 0 until schema.length()) {
                val entity=schema.getJSONObject(i)
                sql.execute(entity.getString("createSql").replace("\${TABLE_NAME}",entity.getString("tableName")))
                val indices=entity.optJSONArray("indices") ?: org.json.JSONArray()
                for(j in 0 until indices.length()) sql.execute(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",entity.getString("tableName")))
            }
            sql.execute("INSERT INTO pets VALUES(1,'Luna','Cat',NULL,0,0,NULL)")
            sql.execute("INSERT INTO pets VALUES(2,'Rex','Dog',NULL,12.5,3,NULL)")
            sql.execute("INSERT INTO activities VALUES(1,1,'FEED',1000,'meal')")
            sql.execute("INSERT INTO reminders VALUES(1,1,'FEED',8,0,'1,2,3')")
            sql.execute("INSERT INTO health_logs VALUES(1,1,'SYMPTOM',NULL,'note',1000)")
            sql.execute("INSERT INTO water_logs VALUES(1,1,100,1000)")
            sql.execute("INSERT INTO notes VALUES(1,1,'note',0,0,1000)")
            sql.execute("INSERT INTO expenses VALUES(1,1,10,'food','note',1000)")
            sql.execute("INSERT INTO chat_sessions VALUES(1,1,'chat',1000,1000,NULL,0,'preview')")
            sql.execute("INSERT INTO chat_messages VALUES(1,1,'user','hello',1000,'request',0,NULL,NULL,'SENT',NULL,NULL)")
            sql.execute("INSERT INTO chat_drafts VALUES(1,'draft',1000)")
            db.autoCommit=false
            TrustMigration.statements.forEach { sql.execute(it) }
            db.commit()
            for(table in listOf("activities","reminders","health_logs","water_logs","notes","expenses","chat_sessions","chat_messages","chat_drafts")) {
                sql.executeQuery("SELECT COUNT(*) FROM $table").use { assertTrue(it.next());assertEquals(table,1,it.getInt(1)) }
            }
            sql.executeQuery("SELECT weightKg,ageYears,birthDate FROM pets WHERE id=1").use { assertTrue(it.next());assertNull(it.getObject(1));assertNull(it.getObject(2));assertNull(it.getObject(3)) }
            sql.executeQuery("SELECT weightKg,ageYears FROM pets WHERE id=2").use { assertTrue(it.next());assertEquals(12.5,it.getDouble(1),0.0);assertEquals(3,it.getInt(2)) }
            sql.executeQuery("PRAGMA foreign_key_check").use { assertFalse(it.next()) }
            // Check the migrated schema against Room's actual exported destination,
            // not only a hand-written subset of the new columns.
            val destination=JSONObject(File("schemas/com.petcare.companionlite.data.local.PetCareDatabase/6.json").readText()).getJSONObject("database").getJSONArray("entities")
            for (i in 0 until destination.length()) {
                val entity=destination.getJSONObject(i)
                val table=entity.getString("tableName")
                val actual=mutableMapOf<String,Pair<String,Boolean>>()
                sql.executeQuery("PRAGMA table_info($table)").use { rows ->
                    while(rows.next()) actual[rows.getString("name")]=rows.getString("type") to (rows.getInt("notnull")==1)
                }
                val fields=entity.getJSONArray("fields")
                assertEquals(table,fields.length(),actual.size)
                for (j in 0 until fields.length()) {
                    val field=fields.getJSONObject(j)
                    assertEquals("$table.${field.getString("columnName")}",field.getString("affinity") to field.optBoolean("notNull", false),actual[field.getString("columnName")])
                }
            }
            sql.execute("DELETE FROM pets WHERE id=1")
            sql.executeQuery("SELECT COUNT(*) FROM chat_messages").use { assertTrue(it.next());assertEquals(0,it.getInt(1)) }
        }
    }
}
