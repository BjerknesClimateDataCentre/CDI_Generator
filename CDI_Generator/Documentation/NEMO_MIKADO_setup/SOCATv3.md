% SOCAT v3 NEMO and MIKADO Setup

# NEMO Model file delimiters

Surrounded by `%%`

Delimiter              Source             Value
---------------------  ----------------   -----------------------------------------------------------------------------------------------------
EXPOCODE               Metadata           `/md:MetaData/md:event/md:label`, discarding anything after `-`
SHIP_CODE              Other delimiter    First four letters of `EXPOCODE`
SHIP_NAME              Metadata           `/md:MetaData/md:event/md:name`
START_DATE_MS          Data               From first record (converted to milliseconds)
END_DATE_MS            Data               From last record (converted to milliseconds)
FIRST_AUTHOR           Metadata           Last Name: `/md:MetaData/md:citation/md:lastName`
                                          First Name: `/md:MetaData/md:citation/md:firstName`
                                          Combine them as `<Last Name>, <First Name>`
FIRST_LINE             Data               Row number of first data line
LATITUDE_FORMAT        Data               `+DD.ddddd`, 
LATITUDE_FIRST_CHAR    Data               Position of first latitude character
LATITUDE_LAST_CHAR     Data               Position of last latitude character (check -1 limit?)
LONGITUDE_FORMAT       Data               `+DDD.ddddd` where number of `D` is the maximum number of digits before the decimal point in the file
LONGITUDE_FIRST_CHAR   Data               Position of first longitude character
LONGITUDE_LAST_CHAR    Data               Position of last longitude character
SENSOR_DEPTH           Metadata           `/md:MetaData/md:extent/md:elevation/md:min` (default to 5)
SALINITY_FIRST_CHAR    Data               Position of first salinity character
SALINITY_LAST_CHAR     Data               Position of last salinity character
SST_FIRST_CHAR         Data               Position of first SST character
SST_LAST_CHAR          Data               Position of last SST character
PRESSURE_FIRST_CHAR    Data               Position of first pressure character
PRESSURE_LAST_CHAR     Data               Position of last pressure character
BATHYMETRY_FIRST_CHAR  Data               Position of first bathymetry character
BATHYMETRY_LAST_CHAR   Data               Position of last bathymetry character
FCO2_FIRST_CHAR        Data               Position of first fCO2 character
FCO2_LAST_CHAR         Data               Position of last fCO2 character


# MIKADO Table `cdi_summary`

Field                  Source             Value
---------------------- ------------------ --------------------------------------------------------------------------------------------------
local_cdi_id           Other delimiter    'SOCATV3_`EXPOCODE`_00001_H71'
platform_id            Other delimiter    `SHIP_CODE` (First four letters of `EXPOCODE`)
dataset_name           Fixed value        'SOCATv3'
doi                    Metadata           `/md:MetaData/md:citation/md:URI`
                                          Remove the text `doi:` from the front
doi_url                Other delimiter    `doi`, preceded by 'https://doi.pangaea.de'
abstract               Data               Citation line from data file
holding_centre         Fixed value        1411 *(Bjerknes Climate Data Centre)*
cruise_name            Other delimiter    `EXPOCODE`
cruise_start_date      Data               From first record (date only)
west_longitude         Metadata           `/md:MetaData/md:extent/md:geographic/md:westBoundLongitude`
east_longitude         Metadata           `/md:MetaData/md:extent/md:geographic/md:eastBoundLongitude`
south_latitude         Metadata           `/md:MetaData/md:extent/md:geographic/md:southBoundLongitude`
north_latitude         Metadata           `/md:MetaData/md:extent/md:geographic/md:northBoundLongitude`
start_date             Data               Date and time from first record *(check format)*
end_date               Data               Date and time from last record *(check format)*
min_depth              Metadata           `/md:MetaData/md:extent/md:elevation/md:min`
max_depth              Metadata           `/md:MetaData/md:extent/md:elevation/md:max`
distribution_data_size After NEMO run     Size of NEMO data file, in Mb
documentation_url      Metadata           `/md:MetaData/md:reference[@relationType="Other version"]/md:URI`
qc_comment             Metadata           `/md:MetaData/md:comment` - First portion of string equating to `Cruise QC flag: *x*`

# MIKADO Table `cdi_fixed_values`
Fixed values to be set for the whole data set

Field                  Value
---------------------- --------------------------------------------------------------------------------------------------
dataset                'SOCATv3'
cdi_partner            1411 *(Bjerknes Climate Data Centre)*
horizontal_datum       3395 *(WGS84)*
revision_date          *SOCAT Release Date*
holding_centre         1411 *(Bjerknes Climate Data Centre)*
access_restriction     'UN'
distributor            1411 *(Bjerknes Climate Data Centre)*
format_name            'ODV'
format_version         '0.4'
qc_name                'SOCAT QC'
qc_date                *SOCAT Release Date*

# MIKADO Table `cdi_platforms`
To be set up for each ship/buoy in the data set before running the CDI Generator, NEMO and MIKADO

Field                  Value
---------------------- --------------------------------------------------------------------------------------------------
id                     Ship code (first 4 letters of EXPO Code)
measuring_area_type    'curve'
platform_type          32 *(VOS line)*, 35 *(VOS Line on fixed route)*, 41 *(Moored surface buoy)*, or 48 *(Mooring)*
originator             EDMO code of institution
start_date             Beginning of valid period for this platform
end_date               End of valid period for this platform
	
# MIKADO Table 'cdi_parameters'
This table contains the paramters provided from each data set, with one record for each data set/parameter.

For SOCAT V3, the `dataset` column is '`SOCATv3', and the parameters supplied are:

 * PSAL
 * TEMP
 * CAPH
 * PCO2

