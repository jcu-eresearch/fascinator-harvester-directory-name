#
# Rules script for sample directory Names data
#
import time
import urllib
import httplib

from com.googlecode.fascinator.api.storage import StorageException
from com.googlecode.fascinator.common import JsonObject
from com.googlecode.fascinator.common import JsonSimple
from com.googlecode.fascinator.common.storage import StorageUtils
from com.googlecode.fascinator.common import FascinatorHome

from java.lang import Exception
from java.lang import String
from java.util import HashSet

class IndexData:
    def __init__(self):
        pass

    def __activate__(self, context):
        # Prepare variables
        self.index = context["fields"]
        self.indexer = context["indexer"]
        self.object = context["object"]
        self.payload = context["payload"]
        self.params = context["params"]
        self.utils = context["pyUtils"]
        self.config = context["jsonConfig"]
        self.log = context["log"]
        self.redboxVersion = self.config.getString("", "redbox.version.string")
        
        # Common data
        self.__newDoc()
        
        # Real metadata
        if self.itemType == "object":
            self.__basicData()
            self.__metadata()

        # Make sure security comes after workflows
        self.__security(self.oid, self.index)

    def __newDoc(self):
        self.oid = self.object.getId()
        self.pid = self.payload.getId()
        metadataPid = self.params.getProperty("metaPid", "DC")

        self.utils.add(self.index, "storage_id", self.oid)
        if self.pid == metadataPid:
            self.itemType = "object"
        else:
            self.oid += "/" + self.pid
            self.itemType = "datastream"
            self.utils.add(self.index, "identifier", self.pid)

        self.utils.add(self.index, "id", self.oid)
        self.utils.add(self.index, "item_type", self.itemType)
        self.utils.add(self.index, "last_modified", time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()))
        self.utils.add(self.index, "harvest_config", self.params.getProperty("jsonConfigOid"))
        self.utils.add(self.index, "harvest_rules",  self.params.getProperty("rulesOid"))
        self.utils.add(self.index, "display_type", "directory_names")

        self.item_security = []
        
    def __basicData(self):
        self.utils.add(self.index, "repository_name", self.params["repository.name"])
        self.utils.add(self.index, "repository_type", self.params["repository.type"])
        # Persistent Identifiers
        pidProperty = self.config.getString(None, ["curation", "pidProperty"])
        if pidProperty is None:
            self.log.error("No configuration found for persistent IDs!")
        else:
            pid = self.params[pidProperty]
            if pid is not None:
                self.utils.add(self.index, "known_ids", pid)
                self.utils.add(self.index, "pidProperty", pid)
                self.utils.add(self.index, "oai_identifier", pid)
        self.utils.add(self.index, "oai_set", "Directory_Names")
        # Publication
        published = self.params["published"]
        if published is not None:
            self.utils.add(self.index, "published", "true")
        # NLA Integration
        nlaReady = self.params["ready_for_nla"]
        if nlaReady is not None:
            self.utils.add(self.index, "ready_for_nla", "ready")
        nlaProperty = self.config.getString(None, ["curation", "nlaIntegration", "pidProperty"])
        if nlaProperty is not None:
            nlaId = self.params[nlaProperty]
            if nlaId is not None:
                self.utils.add(self.index, "known_ids", nlaId)
                self.utils.add(self.index, "nlaId", nlaId)

    def __metadata(self):
        jsonPayload = self.object.getPayload("metadata.json")
        json = self.utils.getJsonObject(jsonPayload.open())
        jsonPayload.close()

        metadata = json.getObject("metadata")
        self.utils.add(self.index, "dc_identifier", metadata.get("dc.identifier"))
                
        data = json.getObject("data")

        ####Global setting for processing data
        ####These will need to be changed based on you system installation.
        theMintHost = "http://localhost:9001"
        redboxHost  = "localhost:9000"
        collectionRelationTypesFilePath = FascinatorHome.getPath() + "/../portal/default/redbox/workflows/forms/data/"

        ###Using the species name, obtained from the directory name, to replace the text in the Title
        species = data.get("species")
        title = data.get("title")
        title = title.replace("%NAME_OF_FOLDER%", species)
        self.utils.add(self.index, "dc_title", title)

        self.utils.add(self.index, "dc_type", data.get("type"))
        self.utils.add(self.index, "dc_created", time.strftime("%Y-%m-%d", time.gmtime()))
        self.utils.add(self.index, "dc_modified", "")
        self.utils.add(self.index, "dc_language", "")
        self.utils.add(self.index, "dc_coverage.vivo_DateTimeInterval.vivo_start", data.get("temporalCoverage").get("dateFrom"))
        self.utils.add(self.index, "dc_coverage.vivo_DateTimeInterval.vivo_end", data.get("temporalCoverage").get("dateTo"))
        self.utils.add(self.index, "dc_coverage.redbox_timePeriod", "")

        ###Processing the 'spatialCoverage' metadata.
        spatialCoverage = data.get("spatialCoverage")
        for i in range(len(spatialCoverage)):
            location = spatialCoverage[i]
            if  location["type"] == "text":
                self.utils.add(self.index, "dc_coverage.vivo_GeographicLocation." + str(i) + ".type", location["type"])
                self.utils.add(self.index, "dc_coverage.vivo_GeographicLocation." + str(i) + ".redbox_wktRaw", location["value"])
                self.utils.add(self.index, "dc_coverage.vivo_GeographicLocation." + str(i) + ".rdf_PlainLiteral", location["value"])

        ###Processing the 'description' metadata.
        description = data.get("description")
        for i in range(len(description)):
            desc = description[i]
            tempDesc = desc.get("value")
            tempDesc = tempDesc.replace("%NAME_OF_FOLDER%", species)
            if  i == 0:
                self.utils.add(self.index, "dc_description", tempDesc)
            self.utils.add(self.index, "rif_description." + str(i) + ".type", desc["type"])
            self.utils.add(self.index, "rif_description." + str(i) + ".value", tempDesc)

        ###Processing the 'relatedPublication' metadata
        relatedPublication = data.get("relatedPublication")
        if relatedPublication is not None:
            for i in range(len(relatedPublication)):
                publication = relatedPublication[i]
                self.utils.add(self.index, "dc_relation.swrc_Publication." + str(i) + ".dc_identifier", publication["url"])
                self.utils.add(self.index, "dc_relation.swrc_Publication." + str(i) + ".dc_title", publication["title"])

        ###Processing the 'relatedWebsite' metadata
        relatedWebsite = data.get("relatedWebsite")
        count = 0
        for i in range(len(relatedWebsite)):
            website = relatedWebsite[i]
            self.utils.add(self.index, "dc_relation.bibo_Website." + str(i) + ".dc_identifier" , website["url"])
            self.utils.add(self.index, "dc_relation.bibo_Website." + str(i) + ".dc_title" , website["notes"])
            count = i

        ###Processing the 'data_source_website' metadata (override metadata)
        dataSourceWebsites = data.get("data_source_website")
        if  dataSourceWebsites is not None:
            for i in range(len(dataSourceWebsites)):
                website = dataSourceWebsites[i]
                type = website.get("identifier").get("type")
                if type == "uri":
                    count += 1 
                    self.utils.add(self.index, "dc_relation.bibo_Website." + str(count) + ".dc_identifier" , website.get("identifier").get("value"))
                    self.utils.add(self.index, "dc_relation.bibo_Website." + str(count) + ".dc_title" , website["notes"])

        ###Processing the 'relatedCollection' metadata
        #Reading the file here, so we only do it once.
        file = open(collectionRelationTypesFilePath + "collectionRelationTypes.json")
        collectionData = file.read()
        file.close()
        relatedCollection = data.get("relatedCollection")
        for i in range(len(relatedCollection)):
            collection = relatedCollection[i]
            tempIdentifier = collection["identifier"]
            if tempIdentifier is not None:
                tempIdentifier = tempIdentifier.replace("%NAME_OF_FOLDER%", species)
            else:
                tempIdentifier = ""
            self.utils.add(self.index, "dc_relation.vivo_Dataset." + str(i) + ".dc_identifier", tempIdentifier)
            tempTitle = collection.get("title")
            tempTitle = tempTitle.replace("%NAME_OF_FOLDER%", species)
            self.utils.add(self.index, "dc_relation.vivo_Dataset." + str(i) + ".dc_title", tempTitle)
            self.utils.add(self.index, "dc_relation.vivo_Dataset." + str(i) + ".vivo.Relationship.rdf.PlainLiteral", collection["relationship"])
            self.utils.add(self.index, "dc_relation.vivo_Dataset." + str(i) + ".redbox_origin", "on")
            self.utils.add(self.index, "dc_relation.vivo_Dataset." + str(i) + ".redbox_publish", "on")
            #Using the collection data as a lookup to obtain the 'label'
            relationShip = collection.get("relationship")
            jsonSimple = JsonSimple(collectionData)
            jsonObj = jsonSimple.getJsonObject()
            results = jsonObj.get("results")
            #ensuring the Collection Relation Types exist
            if  results:
                for j in range(len(results)):
                    relation = results[j]
                    if  (relationShip == relation.get("id")):
                        self.utils.add(self.index, "dc_relation.vivo_Dataset." + str(i) + ".vivo.Relationship.skos_prefLabel", relation.get("label"))

        ###Processing the 'associatedParty' metadata
        associatedParty = data.get("associatedParty")
        for i in range(len(associatedParty)):
            party = associatedParty[i]
            email = party.get("who").get("value")
            if email is not None:
                #Using the email address to obtain the Party details from The Mint
                #For testing, hard coded email address
                #email = "paul.james@example.edu.au"
                sock = urllib.urlopen(theMintHost + "/mint/default/opensearch/lookup?count=999&searchTerms=Email:" + email)
                mintData = sock.read()
                sock.close()
                jsonSimple = JsonSimple(mintData)
                jsonObj = jsonSimple.getJsonObject()
                results = jsonObj.get("results")
                #Ensuring that the Email identified a Party from The Mint
                if  results:
                    resultMetadata = JsonObject(results.get(0))
                    allData = resultMetadata.get("result-metadata")
                    creator = allData.get("all")
                    whoType = party.get("who").get("type")
                    if ((creator is not None) and (whoType == 'people')):
                        self.utils.add(self.index, "dc_creator.foaf_Person." + str(i) + ".dc_identifier", creator.get("dc_identifier")[0])
                        self.utils.add(self.index, "dc_creator.foaf_Person." + str(i) + ".foaf_name", creator.get("dc_title"))
                        self.utils.add(self.index, "dc_creator.foaf_Person." + str(i) + ".foaf_title", creator.get("Honorific")[0])
                        self.utils.add(self.index, "dc_creator.foaf_Person." + str(i) + ".redbox_isCoPrimaryInvestigator", "off")
                        self.utils.add(self.index, "dc_creator.foaf_Person." + str(i) + ".redbox_isPrimaryInvestigator", "on")
                        self.utils.add(self.index, "dc_creator.foaf_Person." + str(i) + ".foaf_givenName", creator.get("Given_Name")[0])
                        self.utils.add(self.index, "dc_creator.foaf_Person." + str(i) + ".foaf_familyName", creator.get("Family_Name")[0])

        ###Processing 'contactInfo.email' metadata
        contactInfoEmail = data.get("contactInfo").get("email")
        #Using the email address to obtain details from The Mint
        #For testing, hard coded email address
        #contactInfoEmail = "paul.james@example.edu.au"
        sock = urllib.urlopen(theMintHost + "/mint/default/opensearch/lookup?count=999&searchTerms=Email:" + contactInfoEmail)
        mintData = sock.read()
        sock.close()
        jsonSimple = JsonSimple(mintData)
        jsonObj = jsonSimple.getJsonObject()
        results = jsonObj.get("results")
        #Ensuring that the Email identified a Party from The Mint
        if  results:
            resultMetadata = JsonObject(results.get(0))
            allData = resultMetadata.get("result-metadata")
            creator = allData.get("all")
            if (creator is not None):
                self.utils.add(self.index, "locrel_prc.foaf_Person.dc_identifier", creator.get("dc_identifier").toString())
                self.utils.add(self.index, "locrel_prc.foaf_Person.foaf_name", creator.get("dc_title"))
                self.utils.add(self.index, "locrel_prc.foaf_Person.foaf_title", creator.get("Honorific").toString())
                self.utils.add(self.index, "locrel_prc.foaf_Person.foaf_givenName", creator.get("Given_Name").toString())
                self.utils.add(self.index, "locrel_prc.foaf_Person.foaf_familyName", creator.get("Family_Name").toString())

        ###Processing 'coinvestigators' metadata
        coinvestigators = data.get("coinvestigators")
        for i in range(len(coinvestigators)):
            self.utils.add(self.index, "dc_contributor.loclrel_clb." + str(i) + ".foaf_Agent" , coinvestigators[i])            

        ###Processing 'anzsrcFOR' metadata
        anzsrcFOR = data.get("anzsrcFOR")
        for i in range(len(anzsrcFOR)):
            anzsrc = anzsrcFOR[i]
            #Querying against The Mint, but only using the first 4 numbers from anzsrc, this ensure a result
            sock = urllib.urlopen(theMintHost + "/mint/ANZSRC_FOR/opensearch/lookup?count=999&level=http://purl.org/asc/1297.0/2008/for/" + anzsrc[:4])
            mintData = sock.read()
            sock.close()
            jsonSimple = JsonSimple(mintData)
            jsonObj = jsonSimple.getJsonObject()
            results = jsonObj.get("results")      
            #ensuring that anzsrc identified a record in The Mint
            if  results:
                for j in range(len(results)):
                    result = JsonObject(results.get(j))
                    rdfAbout = result.get("rdf:about")
                    target = "http://purl.org/asc/1297.0/2008/for/" + anzsrc
                    if  (rdfAbout == target):
                        self.utils.add(self.index, "dc_subject.anzsrc_for." + str(i) + ".skos_prefLabel" , result.get("skos:prefLabel"))            
                        self.utils.add(self.index, "dc_subject.anzsrc_for." + str(i) + ".rdf:resource" , rdfAbout)            

        ###Processing 'anzsrcSEO' metadata                        
        anzsrcSEO = data.get("anzsrcSEO")
        for i in range(len(anzsrcSEO)):
            anzsrc = anzsrcSEO[i]
            #Querying against The Mint, but only using the first 4 numbers from anzsrc, this ensure a result
            sock = urllib.urlopen(theMintHost + "/mint/ANZSRC_SEO/opensearch/lookup?count=999&level=http://purl.org/asc/1297.0/2008/seo/" + anzsrc[:4])
            mintData = sock.read()
            sock.close()
            jsonSimple = JsonSimple(mintData)
            jsonObj = jsonSimple.getJsonObject()
            results = jsonObj.get("results")      
            #ensuring that anzsrc identified a record in The Mint
            if  results:
                for j in range(len(results)):
                    result = JsonObject(results.get(j))
                    rdfAbout = result.get("rdf:about")
                    target = "http://purl.org/asc/1297.0/2008/seo/" + anzsrc
                    if  (rdfAbout == target):
                        self.utils.add(self.index, "dc_subject.anzsrc_seo." + str(i) + ".skos_prefLabel" , result.get("skos:prefLabel"))            
                        self.utils.add(self.index, "dc_subject.anzsrc_seo." + str(i) + ".rdf:resource" , rdfAbout)            

        ###Processing 'keyword' metadata                        
        keyword = data.get("keyword")
        for i in range(len(keyword)):
            self.utils.add(self.index, "dc_subject.vivo_keyword." + str(i) + ".rdf_PlainLiteral", keyword[i])

        self.utils.add(self.index, "dc_accessRights.skos_prefLabel", data.get("accessRights"))
        self.utils.add(self.index, "dc_license.dc_identifier", data.get("license").get("url"))
        self.utils.add(self.index, "dc_license.skos_prefLabel", data.get("license").get("label"))
        self.utils.add(self.index, "dc_identifier.redbox_origin", "internal")

        dataLocation = data.get("dataLocation")
        dataLocation = dataLocation.replace("%NAME_OF_FOLDER%", species)
        self.utils.add(self.index, "bibo_Website.1.dc_identifier", dataLocation)

        #The following have been intentionally set to blank. No mapping is required for these fields.
        self.utils.add(self.index, "vivo_Location", "")
        self.utils.add(self.index, "redbox_retentionPeriod", data.get("retentionPeriod"))
        self.utils.add(self.index, "dc_extent", "unknown")
        self.utils.add(self.index, "redbox_disposalDate", "")
        self.utils.add(self.index, "locrel_own.foaf_Agent.1_foaf_name", "")
        self.utils.add(self.index, "locrel_dtm.foaf_Agent.foaf_name", "")

        ###Processing 'organizationalGroup' metadata
        organisationalGroup = data.get("organizationalGroup")
        for i in range(len(organisationalGroup)):
            organisation = organisationalGroup[i]
            #Querying against The Mint
            sock = urllib.urlopen(theMintHost + "/mint/Parties_Groups/opensearch/lookup?count=9999&searchTerms=ID:" + organisation)
            mintData = sock.read()
            sock.close()
            jsonSimple = JsonSimple(mintData)
            jsonObj = jsonSimple.getJsonObject()
            results = jsonObj.get("results")      
            #ensuring that anzsrc identified a record in The Mint
            if  results:
                resultMetadata = JsonObject(results.get(0))
                allData = resultMetadata.get("result-metadata")
                orgGroup = allData.get("all")
                self.utils.add(self.index, "foaf_Organization.dc_identifier", orgGroup.get("dc_identifier")[0])
                self.utils.add(self.index, "foaf_Organization.skos_prefLabel", orgGroup.get("Name")[0])


        self.utils.add(self.index, "foaf_fundedBy.foaf_Agent", "")
        self.utils.add(self.index, "foaf_fundedBy.vivo_Grant", "")
        self.utils.add(self.index, "swrc_ResearchProject.dc_title", "")
        self.utils.add(self.index, "locrel_dpt.foaf_Person.foaf_name", "")
        self.utils.add(self.index, "dc_SizeOrDuration", "")
        self.utils.add(self.index, "dc_Policy", "")
        self.utils.add(self.index, "redbox_ManagementPlan", "")
    
    def __security(self, oid, index):
        roles = self.utils.getRolesWithAccess(oid)
        if roles is not None:
            for role in roles:
                self.utils.add(index, "security_filter", role)
        else:
            # Default to guest access if Null object returned
            schema = self.utils.getAccessSchema("derby");
            schema.setRecordId(oid)
            schema.set("role", "guest")
            self.utils.setAccessSchema(schema, "derby")
            self.utils.add(index, "security_filter", "guest")

    def __indexList(self, name, values):
        for value in values:
            self.utils.add(self.index, name, value)
