//import React from "./libs/react.js";
//import htm from "./libs/htm.module.js";
//const html = htm.bind(React.createElement);

function RendiBollo(props) {

    const [resp, setResp] = React.useState({page:1,rendicontazioni:[]});
    const [page, setPage] = React.useState(1);

    React.useEffect(()=>{
        props.setLoading(true)
        fetch(`${baseUrl}/rendicontazioni?page=${page}`)
        .then(data=>data.json())
        .then(cl => {
            setResp(cl)
            props.setLoading(false)
        })
    },[page])


    return html`<div className="gridConfig tabled">

        <span className="alignleft header">Action</span>
        <span className="alignleft header">Filename</span>
        <span className="alignleft header">Inserted</span>
        ${resp.rendicontazioni.map(r=>{
            return html`<${React.Fragment} key=${r.id}>
            <span className="alignleft">
                    <a href="${baseUrl}/rendicontazioni/${r.id}">Download</a>
                </span>
                <span className="alignleft">${r.filename}</span>
                <span className="alignleft">${r.insert}</span>
                
                </${React.Fragment}>`
        })}
        <p className="footer">
            Rendicontazioni totali:${resp.count}
            <span className="pager">
                ${resp.page > 1 && html`<span onClick=${()=>setPage(resp.page-1)}>Prev</span>`}
                Pagina:${resp.page}
                ${(resp.page < Math.ceil(resp.count/resp.perpage)) && html`<span onClick=${()=>setPage(resp.page+1)}>Next</span>`}
            </span>
            
        </p>
    </div>`;
  }

//export default Configuration